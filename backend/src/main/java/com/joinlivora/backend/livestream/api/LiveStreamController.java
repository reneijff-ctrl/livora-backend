package com.joinlivora.backend.livestream.api;

import com.joinlivora.backend.livestream.dto.LiveStreamResponse;
import com.joinlivora.backend.livestream.service.LiveStreamService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamCacheDTO;
import com.joinlivora.backend.streaming.dto.GoLiveRequest;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.wallet.WalletTransactionType;
import com.joinlivora.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController("liveStreamControllerV2")
@RequestMapping("/api")
@RequiredArgsConstructor
public class LiveStreamController {

    private final LiveStreamService liveStreamService;
    private final com.joinlivora.backend.streaming.CreatorGoLiveService creatorGoLiveService;
    private final com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository;
    private final LiveViewerCounterService liveViewerCounterService;
    private final TokenService tokenService;
    private final LiveAccessService liveAccessService;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;

    @PostMapping("/creator/live/start")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<LiveStreamResponse> startLive(
            @AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.RequestBody(required = false) GoLiveRequest request) {
        Long userId = principal.getUserId();
        com.joinlivora.backend.creator.model.Creator creator = creatorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("Creator record not found for user: " + userId));
        
        creatorGoLiveService.goLive(creator.getId(), request);
        
        StreamCacheDTO stream = liveStreamService.getActiveStream(userId);
        if (stream == null) {
            throw new RuntimeException("Failed to start live stream");
        }
        return ResponseEntity.ok(mapToResponse(stream));
    }

    @PostMapping("/creator/live/stop")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<LiveStreamResponse> stopLive(@AuthenticationPrincipal UserPrincipal principal) {
        Stream stream = liveStreamService.stopLiveStream(principal.getUserId());
        return ResponseEntity.ok(mapToResponse(stream));
    }

    @GetMapping("/public/creators/{id}/live")
    public ResponseEntity<LiveStreamResponse> getLiveStatus(@PathVariable Long id) {
        StreamCacheDTO stream = liveStreamService.getActiveStream(id);
        if (stream == null) {
            return ResponseEntity.ok(LiveStreamResponse.builder()
                    .creatorUserId(id)
                    .isLive(false)
                    .status(com.joinlivora.backend.livestream.domain.LiveStreamState.ENDED)
                    .build());
        }
        return ResponseEntity.ok(mapToResponse(stream));
    }

    @GetMapping("/livestream/{creatorUserId}/viewers")
    public ResponseEntity<Map<String, Long>> getViewerCount(@PathVariable Long creatorUserId) {
        return ResponseEntity.ok(Map.of("viewerCount", liveViewerCounterService.getViewerCount(creatorUserId)));
    }

    @PostMapping("/live/{creatorUserId}/unlock")
    public ResponseEntity<Map<String, String>> unlockLiveStream(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long creatorUserId
    ) {
        Long userId = principal.getUserId();

        // 1. Deduct 20 tokens
        tokenService.spendTokens(userId, 20, WalletTransactionType.LIVESTREAM_ADMISSION, creatorUserId.toString());

        // 2. Grant 10 minutes access
        liveAccessService.grantAccess(creatorUserId, userId, Duration.ofMinutes(10));

        return ResponseEntity.ok(Map.of("message", "Stream unlocked for 10 minutes"));
    }

    private LiveStreamResponse mapToResponse(Stream stream) {
        if (stream == null) return null;
        return LiveStreamResponse.builder()
                .id(stream.getId())
                .streamId(stream.getId())
                .roomId(stream.getMediasoupRoomId())
                .streamRoomId(stream.getMediasoupRoomId())
                .streamKey(stream.getStreamKey())
                .creatorUserId(stream.getCreator().getId())
                .isLive(stream.isLive())
                .status(stream.isLive() ? com.joinlivora.backend.livestream.domain.LiveStreamState.LIVE : com.joinlivora.backend.livestream.domain.LiveStreamState.ENDED)
                .startedAt(stream.getStartedAt())
                .endedAt(stream.getEndedAt())
                .title(stream.getTitle())
                .thumbnailUrl(stream.getThumbnailUrl())
                .build();
    }

    private LiveStreamResponse mapToResponse(StreamCacheDTO stream) {
        if (stream == null) return null;
        return LiveStreamResponse.builder()
                .id(stream.getId())
                .streamId(stream.getId())
                .roomId(stream.getMediasoupRoomId())
                .streamRoomId(stream.getMediasoupRoomId())
                .streamKey(stream.getStreamKey())
                .creatorUserId(stream.getCreatorUserId())
                .isLive(stream.isLive())
                .status(stream.isLive() ? com.joinlivora.backend.livestream.domain.LiveStreamState.LIVE : com.joinlivora.backend.livestream.domain.LiveStreamState.ENDED)
                .startedAt(stream.getStartedAt())
                .endedAt(stream.getEndedAt())
                .title(stream.getTitle())
                .thumbnailUrl(stream.getThumbnailUrl())
                .build();
    }
}
