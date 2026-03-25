package com.joinlivora.backend.livestream.service;

import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.livestream.domain.LivestreamStatus;
import com.joinlivora.backend.livestream.dto.UnlockResponse;
import com.joinlivora.backend.livestream.repository.LivestreamSessionRepository;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import com.joinlivora.backend.streaming.service.LivestreamAccessService;
import com.joinlivora.backend.token.TokenTransaction;
import com.joinlivora.backend.token.TokenTransactionRepository;
import com.joinlivora.backend.token.TokenTransactionType;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.wallet.*;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnlockLivestreamService {

    private final UserRepository userRepository;
    private final UserWalletRepository walletRepository;
    private final TokenTransactionRepository tokenTransactionRepository;
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
                    .remainingTokens(getViewerBalance(viewerUserId))
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
                    .remainingTokens(getViewerBalance(viewerUserId))
                    .build();
        }

        // 4. Check tokens >= admissionPrice (with pessimistic lock on wallet row)
        UserWallet wallet = walletRepository.findByUserIdWithLock(viewer)
                .orElseGet(() -> walletRepository.save(UserWallet.builder()
                        .userId(viewer)
                        .balance(0)
                        .updatedAt(Instant.now())
                        .build()));

        long price = session.getAdmissionPrice() != null ? session.getAdmissionPrice().longValue() : 0L;
        if (wallet.getBalance() < price) {
            throw new InsufficientBalanceException("Insufficient tokens");
        }

        // 5. Deduct tokens
        wallet.setBalance(wallet.getBalance() - price);
        wallet.setUpdatedAt(Instant.now());

        // 6. Save user
        userRepository.save(viewer);
        walletRepository.save(wallet);

        // 7. Save TokenTransaction entity
        TokenTransaction transaction = TokenTransaction.builder()
                .user(viewer)
                .amount(-price)
                .type(TokenTransactionType.STREAM_UNLOCK)
                .creatorId(creatorUserId)
                .build();
        tokenTransactionRepository.save(transaction);

        // Grant access in Redis for 7200 seconds
        accessService.grantAccess(session.getId(), viewerUserId, Duration.ofSeconds(7200));

        // Grant access in DB (live_access) so downstream checks (WebSocketInterceptor, HLS) pass
        liveAccessService.grantAccess(creatorUserId, viewerUserId, Duration.ofSeconds(7200));

        // 8. Return remaining tokens
        log.info("UNLOCK-STREAM: Successful for viewerUserId={}, remainingTokens={}", viewerUserId, wallet.getBalance());
        return UnlockResponse.builder()
                .success(true)
                .remainingTokens(wallet.getBalance())
                .build();
    }

    private long getViewerBalance(Long userId) {
        User viewer = userRepository.findById(userId).orElse(null);
        if (viewer == null) return 0;
        return walletRepository.findByUserId(viewer)
                .map(UserWallet::getBalance)
                .orElse(0L);
    }
}
