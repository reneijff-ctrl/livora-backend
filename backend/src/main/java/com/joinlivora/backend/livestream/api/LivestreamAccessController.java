package com.joinlivora.backend.livestream.api;

import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.dto.LivestreamAccessResponse;
import com.joinlivora.backend.streaming.service.LivestreamAccessService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.wallet.WalletTransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/livestream")
@RequiredArgsConstructor
@Slf4j
public class LivestreamAccessController {

    private final LivestreamAccessService accessService;
    private final LiveViewerCounterService liveViewerCounterService;
    private final TokenService tokenService;
    private final StreamRepository streamRepository;
    private final com.joinlivora.backend.presence.service.CreatorPresenceService creatorPresenceService;

    @GetMapping("/{creatorUserId}/access")
    public ResponseEntity<LivestreamAccessResponse> getAccess(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long creatorUserId
    ) {
        Long viewerUserId = principal != null ? principal.getUserId() : null;

        // 1. Fetch active unified Stream (Single Source of Truth)
        var liveStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId);

        if (liveStreams.isEmpty()) {
            return ResponseEntity.ok(LivestreamAccessResponse.builder()
                    .hasAccess(false)
                    .isLive(false)
                    .build());
        }

        var unifiedStream = liveStreams.get(0);

        // 2. Determine access via UUID-based key (no legacy session fallback)
        boolean hasAccess = accessService.hasAccess(unifiedStream.getId(), viewerUserId);

        long viewerCount = liveViewerCounterService.getViewerCount(creatorUserId);

        log.info("event=ACCESS_CHECK creatorUserId={} viewerUserId={} streamId={} isLive=true isPaid={} hasAccess={} viewerCount={}",
                creatorUserId, viewerUserId, unifiedStream.getId(), unifiedStream.isPaid(), hasAccess, viewerCount);

        return ResponseEntity.ok(LivestreamAccessResponse.builder()
                .hasAccess(hasAccess)
                .isPaid(unifiedStream.isPaid())
                .isLive(true)
                .viewerCount(viewerCount)
                .admissionPrice(unifiedStream.getAdmissionPrice())
                .streamId(unifiedStream.getId())
                .streamRoomId(unifiedStream.getMediasoupRoomId())
                .build());
    }


    @PostMapping("/{creatorUserId}/purchase-access")
    public ResponseEntity<Map<String, Boolean>> purchaseAccess(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long creatorUserId
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        Long viewerUserId = principal.getUserId();

        // 1. Fetch active unified Stream (Single Source of Truth)
        var liveStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId);
        if (liveStreams.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false));
        }
        var unifiedStream = liveStreams.get(0);

        // 2. Check idempotency via UUID-based access key
        if (accessService.hasAccess(unifiedStream.getId(), viewerUserId)) {
            return ResponseEntity.ok(Map.of("success", true));
        }

        if (!unifiedStream.isPaid()) {
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }

        long price = unifiedStream.getAdmissionPrice() != null ? unifiedStream.getAdmissionPrice().longValue() : 0L;
        if (price <= 0) {
            log.warn("PURCHASE_ACCESS: Paid flag set but non-positive admissionPrice for streamId={}", unifiedStream.getId());
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }

        // Deduct tokens from viewer wallet
        tokenService.spendTokens(viewerUserId, price, WalletTransactionType.LIVESTREAM_ADMISSION, creatorUserId.toString());

        // Grant access for 7200 seconds via UUID-based key
        accessService.grantAccess(unifiedStream.getId(), viewerUserId, Duration.ofSeconds(7200));

        return ResponseEntity.ok(Map.of("success", true));
    }
}
