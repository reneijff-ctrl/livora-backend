package com.joinlivora.backend.streaming;

import com.joinlivora.backend.config.MetricsService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Fallback / debug HLS delivery via Spring Boot.
 *
 * <p><strong>Production path</strong>: Nginx serves HLS files directly from the
 * {@code streams_data} volume at {@code /hls/{streamId}/…} — no JVM involvement.
 * This controller is kept for:
 * <ul>
 *   <li>Zero-downtime rollout: old {@code /api/hls/…} URLs still resolve during
 *       the transition period while the frontend switches to the new Nginx path.</li>
 *   <li>Local dev environments where Nginx direct serving may not be configured.</li>
 * </ul>
 *
 * <p><strong>DO NOT add new features here.</strong>  Segment requests that reach
 * this controller in production indicate a misconfiguration — they should be
 * absorbed by Nginx before ever reaching Tomcat.
 */
@RestController
@RequestMapping("/api/hls")
@RequiredArgsConstructor
@Slf4j
public class HlsProxyController {

    private final StreamRepository streamRepository;
    private final UserService userService;
    private final MeterRegistry meterRegistry;
    private final MetricsService metricsService;
    private final com.joinlivora.backend.livestream.service.LiveStreamService liveStreamService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.joinlivora.backend.resilience.DatabaseCircuitBreakerService dbCircuitBreaker;

    @Value("${hls.directory:/var/www/hls}")
    private String hlsDirectory;

    @Value("${hls.recordings-directory:${recordings.directory:/var/www/recordings/hls}}")
    private String recordingsDirectory;

    // ── New streamId-only endpoints (production) ────────────────────────────

    /**
     * Serves an HLS playlist file ({@code master.m3u8}, {@code 720p.m3u8}, etc.)
     * keyed only by {@code streamId} (UUID).  This matches the on-disk path
     * written by the transcode-worker: {@code /streams/{streamId}/{filename}}.
     */
    @GetMapping("/{streamId}/{filename:.+\\.m3u8}")
    public ResponseEntity<Resource> getPlaylist(
            @PathVariable UUID streamId,
            @PathVariable String filename,
            Principal principal
    ) {
        if (!validateAccess(streamId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        File file = resolveFile(streamId.toString(), filename);
        if (file == null) {
            log.warn("HLS_FALLBACK: playlist not found: streamId={} file={}", streamId, filename);
            return ResponseEntity.notFound().build();
        }

        meterRegistry.counter("streaming.hls.fallback", "type", "playlist").increment();
        metricsService.getHlsFallbackServed().increment();
        log.debug("HLS_FALLBACK: serving playlist via Spring (should be Nginx): streamId={} file={}", streamId, filename);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .header("Access-Control-Allow-Origin", "*")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noCache())
                .body(new FileSystemResource(file));
    }

    /**
     * Serves an HLS transport-stream segment ({@code 720p_0001.ts}, etc.)
     * keyed only by {@code streamId} (UUID).
     */
    @GetMapping("/{streamId}/{segment:.+\\.ts}")
    public ResponseEntity<Resource> getSegment(
            @PathVariable UUID streamId,
            @PathVariable String segment,
            Principal principal
    ) {
        if (!validateAccess(streamId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        File file = resolveFile(streamId.toString(), segment);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        meterRegistry.counter("streaming.hls.fallback", "type", "segment").increment();
        metricsService.getHlsFallbackServed().increment();
        log.debug("HLS_FALLBACK: serving segment via Spring (should be Nginx): streamId={} file={}", streamId, segment);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "video/mp2t")
                .header("Access-Control-Allow-Origin", "*")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(new FileSystemResource(file));
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    /**
     * Resolves a file from the live HLS directory, with fallback to the
     * recordings directory for VOD content.
     *
     * @param dirKey  the subdirectory name (always {@code streamId.toString()})
     * @param filename the file name inside that directory
     * @return the first existing {@link File}, or {@code null} if not found
     */
    private File resolveFile(String dirKey, String filename) {
        File live = new File(hlsDirectory + "/" + dirKey + "/" + filename);
        if (live.exists()) return live;

        File recording = new File(recordingsDirectory + "/" + dirKey + "/" + filename);
        if (recording.exists()) return recording;

        return null;
    }

    private boolean validateAccess(UUID streamId, Principal principal) {
        @SuppressWarnings("unchecked")
        Stream stream = (dbCircuitBreaker != null)
                ? ((java.util.Optional<Stream>) dbCircuitBreaker.execute(
                        () -> streamRepository.findByIdWithCreator(streamId),
                        java.util.Optional.empty(), "hlsStreamLookup")).orElse(null)
                : streamRepository.findByIdWithCreator(streamId).orElse(null);
        if (stream == null) {
            return false;
        }

        try {
            if (!liveStreamService.isStreamActive(stream.getCreator().getId())) {
                return false;
            }
        } catch (Exception e) {
            // Fail closed: if we cannot determine state, deny access.
            return false;
        }

        User user = null;
        if (principal != null) {
            user = userService.resolveUserFromSubject(principal.getName()).orElse(null);
        }

        return liveStreamService.validateViewerAccess(stream, user);
    }
}
