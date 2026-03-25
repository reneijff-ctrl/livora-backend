package com.joinlivora.backend.livestream.api;

import com.joinlivora.backend.livestream.domain.LivestreamSession;
import com.joinlivora.backend.livestream.domain.LivestreamStatus;
import com.joinlivora.backend.livestream.repository.LivestreamSessionRepository;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.streaming.Stream;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/livestream")
@RequiredArgsConstructor
@Slf4j
public class LivestreamAccessController {

    private final LivestreamAccessService accessService;
    private final LiveViewerCounterService liveViewerCounterService;
    private final TokenService tokenService;
    private final StreamRepository streamRepository;
    private final LivestreamSessionRepository sessionRepository;
    private final com.joinlivora.backend.presence.service.CreatorPresenceService creatorPresenceService;

    @GetMapping("/{creatorUserId}/access")
    public ResponseEntity<LivestreamAccessResponse> getAccess(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long creatorUserId
    ) {
        Long viewerUserId = principal != null ? principal.getUserId() : null;

        // 1. Fetch active session
        var sessionOpt = sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(
                creatorUserId, LivestreamStatus.LIVE);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.ok(LivestreamAccessResponse.builder()
                    .hasAccess(false)
                    .isLive(false)
                    .build());
        }

        var session = sessionOpt.get();

        // 2. Determine access
        boolean hasAccess = accessService.hasAccess(session.getId(), viewerUserId);

        long viewerCount = liveViewerCounterService.getViewerCount(creatorUserId);

        log.info("event=ACCESS_CHECK creatorUserId={} viewerUserId={} isLive=true isPaid={} hasAccess={} viewerCount={}",
                creatorUserId, viewerUserId, session.isPaid(), hasAccess, viewerCount);

        var liveStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId);
        if (liveStreams.isEmpty()) {
            log.warn("LIVESTREAM: Creator {} is LIVE but has no active unified Stream entity! UI may be inconsistent.", creatorUserId);
        }

        return ResponseEntity.ok(LivestreamAccessResponse.builder()
                .hasAccess(hasAccess)
                .isPaid(session.isPaid())
                .isLive(true)
                .viewerCount(viewerCount)
                .admissionPrice(session.getAdmissionPrice() != null ? session.getAdmissionPrice() : java.math.BigDecimal.ZERO)
                .sessionId(session.getId())
                .streamRoomId(liveStreams.isEmpty() ? null : liveStreams.get(0).getMediasoupRoomId())
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

        // Fetch active session
        var sessionOpt = sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(
                creatorUserId, LivestreamStatus.LIVE);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false));
        }
        var session = sessionOpt.get();

        // If viewer already has access, treat as idempotent success
        if (accessService.hasAccess(session.getId(), viewerUserId)) {
            return ResponseEntity.ok(Map.of("success", true));
        }

        if (!session.isPaid()) {
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }

        long price = session.getAdmissionPrice() != null ? session.getAdmissionPrice().longValue() : 0L;
        if (price <= 0) {
            log.warn("PURCHASE_ACCESS: Paid flag set but non-positive admissionPrice for sessionId={}", session.getId());
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }

        // Deduct tokens from viewer wallet
        tokenService.spendTokens(viewerUserId, price, WalletTransactionType.LIVESTREAM_ADMISSION, creatorUserId.toString());

        // Grant access for 7200 seconds
        accessService.grantAccess(session.getId(), viewerUserId, Duration.ofSeconds(7200));

        return ResponseEntity.ok(Map.of("success", true));
    }
}
