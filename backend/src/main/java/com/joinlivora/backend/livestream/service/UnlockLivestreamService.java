package com.joinlivora.backend.livestream.service;

import com.joinlivora.backend.livestream.domain.LivestreamStatus;
import com.joinlivora.backend.livestream.dto.UnlockResponse;
import com.joinlivora.backend.livestream.repository.LivestreamSessionRepository;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import com.joinlivora.backend.streaming.service.LivestreamAccessService;
import com.joinlivora.backend.token.TokenWalletService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.wallet.WalletTransactionType;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnlockLivestreamService {

    private final UserRepository userRepository;
    private final TokenWalletService tokenWalletService;
    private final LivestreamSessionRepository sessionRepository;
    private final LivestreamAccessService accessService;
    private final LiveAccessService liveAccessService;

    @Transactional
    public UnlockResponse unlockStream(Long creatorUserId, Long viewerUserId) {
        log.info("UNLOCK-STREAM: creatorUserId={} viewerUserId={}", creatorUserId, viewerUserId);
        
        // 1. Fetch active session
        var sessionOpt = sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(
                creatorUserId, LivestreamStatus.LIVE);

        if (sessionOpt.isEmpty()) {
            throw new IllegalStateException("Stream is not live");
        }
        var session = sessionOpt.get();

        // 2. If not paid → return success (no charge)
        if (session.isFree()) {
            log.info("UNLOCK-STREAM: Stream not paid, granting access for free");
            accessService.grantAccess(session.getId(), viewerUserId, Duration.ofSeconds(7200));
            return UnlockResponse.builder()
                    .success(true)
                    .remainingTokens(tokenWalletService.getTotalBalance(viewerUserId))
                    .build();
        }

        // 3. Lock viewer FIRST — serializes all concurrent unlock attempts for the same viewer
        User viewer = userRepository.findByIdForUpdate(viewerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Idempotency check INSIDE critical section: if viewer already has access, return success (no charge)
        if (accessService.hasAccess(session.getId(), viewerUserId)) {
            log.info("UNLOCK-STREAM: Viewer already has access to sessionId={}", session.getId());
            return UnlockResponse.builder()
                    .success(true)
                    .remainingTokens(tokenWalletService.getTotalBalance(viewerUserId))
                    .build();
        }

        // 4. Deduct tokens via TokenWalletService (handles pessimistic locking, balance check,
        //    WalletTransaction creation, and WebSocket wallet update atomically)
        long price = session.getAdmissionPrice() != null ? session.getAdmissionPrice().longValue() : 0L;
        tokenWalletService.deductTokens(viewerUserId, price, WalletTransactionType.LIVESTREAM_ADMISSION, creatorUserId.toString());

        // 5. Grant access in Redis for 7200 seconds
        accessService.grantAccess(session.getId(), viewerUserId, Duration.ofSeconds(7200));

        // Grant access in DB (live_access) so downstream checks (WebSocketInterceptor, HLS) pass
        liveAccessService.grantAccess(creatorUserId, viewerUserId, Duration.ofSeconds(7200));

        // 6. Return remaining tokens
        long remainingTokens = tokenWalletService.getTotalBalance(viewerUserId);
        log.info("UNLOCK-STREAM: Successful for viewerUserId={}, remainingTokens={}", viewerUserId, remainingTokens);
        return UnlockResponse.builder()
                .success(true)
                .remainingTokens(remainingTokens)
                .build();
    }
}
