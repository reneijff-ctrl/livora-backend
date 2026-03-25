package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/hls")
@RequiredArgsConstructor
@Slf4j
public class HlsProxyController {

    private final StreamService streamService;
    private final StreamRepository streamRepository;
    private final UserService userService;
    private final MeterRegistry meterRegistry;
    private final com.joinlivora.backend.livestream.service.LiveStreamService liveStreamService;

    private final Map<String, Bucket> segmentBuckets = new ConcurrentHashMap<>();

    @Value("${hls.directory:/var/www/hls}")
    private String hlsDirectory;

    @Value("${recordings.directory:/var/www/recordings/hls}")
    private String recordingsDirectory;

    private final Map<String, String> userLastVariant = new ConcurrentHashMap<>();

    @GetMapping("/{streamId}/{streamKey}/{filename:.+\\.m3u8}")
    public ResponseEntity<Resource> getPlaylist(
            @PathVariable UUID streamId,
            @PathVariable String streamKey,
            @PathVariable String filename,
            Principal principal
    ) {
        if (!validateAccess(streamId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Check live directory first, then recordings
        File file = new File(hlsDirectory + "/" + streamKey + "/" + filename);
        if (!file.exists()) {
            file = new File(recordingsDirectory + "/" + streamKey + "/" + filename);
        }

        if (!file.exists()) {
            log.warn("HLS: Playlist not found: {}", file.getAbsolutePath());
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .cacheControl(CacheControl.noCache())
                .body(new FileSystemResource(file));
    }

    @GetMapping("/{streamId}/{streamKey}/{variant}/{segment}.ts")
    public ResponseEntity<Resource> getVariantSegment(
            @PathVariable UUID streamId,
            @PathVariable String streamKey,
            @PathVariable String variant,
            @PathVariable String segment,
            Principal principal
    ) {
        if (principal != null && !tryConsumeSegment(principal.getName())) {
            log.warn("HLS: Rate limit exceeded for segments: creator={}", principal.getName());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        if (!validateAccess(streamId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        File file = new File(hlsDirectory + "/" + streamKey + "/" + variant + "/" + segment + ".ts");
        if (!file.exists()) {
            file = new File(recordingsDirectory + "/" + streamKey + "/" + variant + "/" + segment + ".ts");
        }

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        log.info("STREAM: HLS segment requested: streamId={}, variant={}", streamId, variant);
        if (principal != null) {
            String lastVariant = userLastVariant.put(principal.getName() + ":" + streamId, variant);
            if (lastVariant != null && !lastVariant.equals(variant)) {
                log.info("STREAM: Bitrate change detected for creator {}: {} -> {}", principal.getName(), lastVariant, variant);
            }
        }
        meterRegistry.counter("streaming.hls.segments", "variant", variant).increment();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "video/mp2t")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(new FileSystemResource(file));
    }

    @GetMapping("/{streamId}/{streamKey}/{segment}.ts")
    public ResponseEntity<Resource> getSegment(
            @PathVariable UUID streamId,
            @PathVariable String streamKey,
            @PathVariable String segment,
            Principal principal
    ) {
        if (principal != null && !tryConsumeSegment(principal.getName())) {
            log.warn("HLS: Rate limit exceeded for segments: creator={}", principal.getName());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        if (!validateAccess(streamId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        File file = new File(hlsDirectory + "/" + streamKey + "/" + segment + ".ts");
        if (!file.exists()) {
            file = new File(recordingsDirectory + "/" + streamKey + "/" + segment + ".ts");
        }

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        log.info("STREAM: HLS segment requested: streamId={}, variant=default", streamId);
        meterRegistry.counter("streaming.hls.segments", "variant", "default").increment();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "video/mp2t")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(new FileSystemResource(file));
    }

    private boolean validateAccess(UUID streamId, Principal principal) {
        Stream stream = streamRepository.findByIdWithCreator(streamId).orElse(null);
        if (stream == null) {
            return false;
        }

        // Enforce stream state: only allow watch when LIVE
        try {
            if (!liveStreamService.isStreamActive(stream.getCreator().getId())) {
                return false;
            }
        } catch (Exception e) {
            // If service is not available or errors, deny by default for safety
            return false;
        }

        User user = null;
        if (principal != null) {
            user = userService.resolveUserFromSubject(principal.getName()).orElse(null);
        }

        return liveStreamService.validateViewerAccess(stream, user);
    }

    private boolean tryConsumeSegment(String userKey) {
        // HLS Segments: 120 segments per minute (approx 6 minutes of video at 3s segments)
        // This is a generous limit to allow for buffering and variant switching.
        Bucket bucket = segmentBuckets.computeIfAbsent(userKey, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(120)
                        .refillIntervally(120, Duration.ofMinutes(1))
                        .build())
                .build());
        return bucket.tryConsume(1);
    }

    // Visible for testing
    void resetRateLimits() {
        segmentBuckets.clear();
    }
}
