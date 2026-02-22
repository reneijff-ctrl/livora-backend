package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.monetization.SuperTipHighlightTracker;
import com.joinlivora.backend.monetization.dto.SuperTipResponse;
import com.joinlivora.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/stream")
@RequiredArgsConstructor
@Slf4j
public class LiveStreamController {

    private final StreamService streamService;
    private final LiveStreamService liveStreamService;
    private final UserService userService;
    private final com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;
    private final com.joinlivora.backend.user.UserRepository userRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final SuperTipHighlightTracker highlightTracker;
    private final com.joinlivora.backend.livestream.service.LiveStreamService liveStreamServiceV2;

    @PostMapping("/start")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CREATOR') and @securityService.isActiveCreator(principal.userId))")
    public ResponseEntity<StreamRoom> startStream(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody StartStreamRequest request
    ) {
        User creator = userService.getById(principal.getUserId());
        StreamRoom room = streamService.startStream(
                creator,
                request.getTitle(),
                request.getDescription(),
                request.getMinChatTokens(),
                request.isPaid(),
                request.getPricePerMessage(),
                request.getAdmissionPrice(),
                request.isRecordingEnabled()
        );
        return ResponseEntity.ok(room);
    }

    @PostMapping("/stop")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CREATOR') and @securityService.isActiveCreator(principal.userId))")
    public ResponseEntity<StreamRoom> stopStream(@AuthenticationPrincipal UserPrincipal principal) {
        User creator = userService.getById(principal.getUserId());
        return ResponseEntity.ok(streamService.stopStream(creator));
    }

    @GetMapping("/ingest-info")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CREATOR') and @securityService.isActiveCreator(principal.userId))")
    public ResponseEntity<Map<String, String>> getIngestInfo(@AuthenticationPrincipal UserPrincipal principal) {
        User creator = userService.getById(principal.getUserId());
        LiveStream stream = liveStreamRepository.findByCreatorId(creator.getId())
                .orElseThrow(() -> new RuntimeException("Stream not found"));

        return ResponseEntity.ok(Map.of(
                "server", "rtmp://api.joinlivora.com/live",
                "streamKey", stream.getStreamKey()
        ));
    }

    @GetMapping("/{id}/hls")
    public ResponseEntity<Map<String, String>> getHlsUrl(
            @PathVariable UUID id,
            java.security.Principal principal
    ) {
        LiveStream stream = liveStreamRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stream not found"));

        User user = null;
        if (principal != null) {
            user = userService.getByEmail(principal.getName());
        }

        if (!liveStreamService.validateViewerAccess(stream, user)) {
            log.warn("SECURITY: Access denied for HLS URL: creator={} stream={}",
                    user != null ? user.getEmail() : "anonymous", id);
            return ResponseEntity.status(403).build();
        }

        // Enforce V2 state gating: WATCH allowed only when state == LIVE
        try {
            if (liveStreamServiceV2.getActiveStream(stream.getCreatorId()) == null) {
                log.warn("SECURITY: HLS URL denied due to non-LIVE V2 state: creator={} stream={}",
                        stream.getCreatorId(), id);
                return ResponseEntity.status(403).build();
            }
        } catch (Exception e) {
            log.error("V2 gating check failed: {}", e.getMessage());
            return ResponseEntity.status(403).build();
        }

        // Return a proxy URL that includes the streamId and streamKey
        // This allows the HlsProxyController to validate access for every request
        String proxyUrl = "/hls/" + id + "/" + stream.getStreamKey() + "/index.m3u8";
        
        return ResponseEntity.ok(Map.of("url", proxyUrl));
    }

    @GetMapping("/live")
    public ResponseEntity<List<StreamRoom>> getLiveStreams() {
        return ResponseEntity.ok(streamService.getLiveStreams());
    }

    @GetMapping("/vod")
    public ResponseEntity<List<LiveStream>> getVodStreams() {
        return ResponseEntity.ok(liveStreamRepository.findAllByIsLiveFalseAndRecordingPathIsNotNull());
    }

    @GetMapping("/{creatorId}")
    public ResponseEntity<StreamRoom> getStreamByCreator(@PathVariable Long creatorId) {
        User creator = creatorProfileService.getUserByProfileId(creatorId);
        return ResponseEntity.ok(streamService.getCreatorRoom(creator));
    }

    @GetMapping("/room/{id}")
    public ResponseEntity<StreamRoom> getStream(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(streamService.getRoom(id));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getStreamStatus(@PathVariable UUID id) {
        StreamRoom room = streamService.getRoom(id);
        return ResponseEntity.ok(Map.of(
                "isLive", room.isLive(),
                "viewerCount", room.getViewerCount()
        ));
    }

    @GetMapping("/{id}/highlight")
    public ResponseEntity<SuperTipResponse> getActiveHighlight(@PathVariable UUID id) {
        return highlightTracker.getActiveHighlight(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/auth")
    public ResponseEntity<Void> authenticateRTMP(
            @RequestParam("name") String streamKey,
            @RequestParam(value = "addr", required = false) String addr
    ) {
        log.info("RTMP Auth request: key={} from addr={}", streamKey, addr);
        boolean authorized = liveStreamService.verifyStreamKeyAndStart(streamKey);
        
        if (authorized) {
            return ResponseEntity.ok().build();
        } else {
            log.warn("RTMP Auth failed for key: {}", streamKey);
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/auth-done")
    public ResponseEntity<Void> authenticateRTMPDone(
            @RequestParam("name") String streamKey
    ) {
        log.info("RTMP Auth Done: key={}", streamKey);
        liveStreamService.verifyStreamKeyAndStop(streamKey);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/record-done")
    public ResponseEntity<Void> recordDone(
            @RequestParam("name") String streamKey,
            @RequestParam("path") String path
    ) {
        log.info("RTMP Record Done: key={} path={}", streamKey, path);
        liveStreamService.updateRecordingPath(streamKey, path);
        return ResponseEntity.ok().build();
    }
}
