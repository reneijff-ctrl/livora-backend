package com.joinlivora.backend.livestream.service;

import com.joinlivora.backend.livestream.dto.UnlockResponse;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnlockLivestreamService {

    private static final int GRANT_MAX_ATTEMPTS = 3;
    private static final long GRANT_RETRY_DELAY_MS = 75L;

    private final UserRepository userRepository;
    private final TokenWalletService tokenWalletService;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;
    private final LivestreamAccessService accessService;
    private final LiveAccessService liveAccessService;

    @Transactional
    public UnlockResponse unlockStream(Long creatorUserId, Long viewerUserId) {
        log.info("UNLOCK-STREAM: creatorUserId={} viewerUserId={}", creatorUserId, viewerUserId);

        // 1. Fetch active unified Stream (Single Source of Truth)
        var liveStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId);
        if (liveStreams.isEmpty()) {
            throw new IllegalStateException("Stream is not live");
        }
        var unifiedStream = liveStreams.get(0);
        UUID streamId = unifiedStream.getId();

        // 2. If not paid → grant access for free and return
        if (!unifiedStream.isPaid()) {
            log.info("UNLOCK-STREAM: Stream not paid, granting access for free");
            grantRedisAccessWithRetry(streamId, viewerUserId, Duration.ofSeconds(7200));
            return UnlockResponse.builder()
                    .success(true)
                    .remainingTokens(tokenWalletService.getTotalBalance(viewerUserId))
                    .build();
        }

        // 3. Lock viewer FIRST — serializes all concurrent unlock attempts for the same viewer
        User viewer = userRepository.findByIdForUpdate(viewerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Idempotency check INSIDE critical section: if viewer already has access, return success (no charge)
        if (accessService.hasAccess(streamId, viewerUserId)) {
            log.info("UNLOCK-STREAM: Viewer already has access to streamId={}", streamId);
            return UnlockResponse.builder()
                    .success(true)
                    .remainingTokens(tokenWalletService.getTotalBalance(viewerUserId))
                    .build();
        }

        // 4. Deduct tokens — pessimistic locking, balance check, WalletTransaction + WebSocket update
        long price = unifiedStream.getAdmissionPrice() != null ? unifiedStream.getAdmissionPrice().longValue() : 0L;
        tokenWalletService.deductTokens(viewerUserId, price, WalletTransactionType.LIVESTREAM_ADMISSION, creatorUserId.toString());

        // 5. Grant Redis access with retry — failure here triggers transaction rollback,
        //    reversing the token deduction above.
        grantRedisAccessWithRetry(streamId, viewerUserId, Duration.ofSeconds(7200));

        // 6. Grant DB access (live_access) — runs inside this transaction; any exception
        //    propagates naturally and rolls back both the token deduction and this write.
        liveAccessService.grantAccess(creatorUserId, viewerUserId, Duration.ofSeconds(7200));

        // 7. Return remaining tokens
        long remainingTokens = tokenWalletService.getTotalBalance(viewerUserId);
        log.info("UNLOCK-STREAM: Successful for viewerUserId={}, remainingTokens={}", viewerUserId, remainingTokens);
        return UnlockResponse.builder()
                .success(true)
                .remainingTokens(remainingTokens)
                .build();
    }

    /**
     * Attempts to write the Redis access key up to {@link #GRANT_MAX_ATTEMPTS} times with a short
     * delay between attempts. If all attempts fail, throws a {@link RuntimeException} so that the
     * surrounding {@code @Transactional} method rolls back the token deduction, leaving the viewer's
     * balance intact.
     */
    private void grantRedisAccessWithRetry(UUID streamId, Long viewerUserId, Duration duration) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= GRANT_MAX_ATTEMPTS; attempt++) {
            try {
                accessService.grantAccess(streamId, viewerUserId, duration);
                return; // success
            } catch (Exception ex) {
                lastException = ex;
                log.warn("UNLOCK-STREAM: Redis access grant attempt {}/{} failed for streamId={} viewerUserId={}: {}",
                        attempt, GRANT_MAX_ATTEMPTS, streamId, viewerUserId, ex.getMessage());
                if (attempt < GRANT_MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(GRANT_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("CRITICAL: Access grant failed after payment — all {} attempts exhausted for streamId={} viewerUserId={}. Rolling back token deduction.",
                GRANT_MAX_ATTEMPTS, streamId, viewerUserId);
        throw new RuntimeException(
                "CRITICAL: Failed to grant stream access in Redis after " + GRANT_MAX_ATTEMPTS + " attempts " +
                "for streamId=" + streamId + " viewerUserId=" + viewerUserId + ". Transaction will rollback.",
                lastException);
    }
}
