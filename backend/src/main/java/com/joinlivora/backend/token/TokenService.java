package com.joinlivora.backend.token;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.wallet.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service("tokenService")
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final UserWalletRepository tokenBalanceRepository;
    private final WalletTransactionRepository tokenTransactionRepository;
    private final TokenPackageRepository tokenPackageRepository;
    private final TipRecordRepository tipRecordRepository;
    private final CreatorEarningsRepository creatorEarningsRepository;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;
    private final UserRepository userRepository;
    private final TokenWalletService tokenWalletService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final StringRedisTemplate redisTemplate;

    private static final String ACCESS_KEY_PREFIX = "livestream:access:";


    public List<TokenPackage> getActivePackages() {
        return tokenPackageRepository.findAllByActiveTrue();
    }

    public UserWallet getBalance(User user) {
        return tokenWalletService.getOrCreateWallet(user.getId());
    }

    @Transactional
    public void creditTokens(User user, long amount, String reference) {
        tokenWalletService.addTokens(user.getId(), amount, WalletTransactionType.PURCHASE, reference);
        
        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.PAYMENT_SUCCEEDED, // Reuse or create new type
                user,
                Map.of("tokenAmount", amount, "type", "purchase", "referenceId", reference)
        );
    }

    @Transactional
    public void deductTokens(User user, long amount, WalletTransactionType reason, String reference) {
        tokenWalletService.deductTokens(user.getId(), amount, reason, reference);
    }

    @Transactional
    public void spendTokens(Long userId, long amount, WalletTransactionType source, String referenceId) {
        if (source == WalletTransactionType.LIVESTREAM_ADMISSION) {
            log.info("LIVESTREAM_ADMISSION_DEBIT userId={} amount={} creator={}", userId, amount, referenceId);
        }
        tokenWalletService.deductTokens(userId, amount, source, referenceId);
    }

    public List<WalletTransaction> getTransactionHistory(User user) {
        return tokenTransactionRepository.findAllByUserIdOrderByCreatedAtDesc(user);
    }

    public CreatorEarnings getCreatorEarnings(User user) {
        return creatorEarningsRepository.findByUser(user)
                .orElseGet(() -> creatorEarningsRepository.save(
                        CreatorEarnings.builder()
                                .user(user)
                                .totalEarnedTokens(0)
                                .availableTokens(0)
                                .build()
                ));
    }

    @Transactional
    public void updateCreatorEarnings(CreatorEarnings earnings) {
        creatorEarningsRepository.save(earnings);
    }

    public boolean hasEnoughTokens(Long userId, Long creatorUserId) {
        if (userId.equals(creatorUserId)) return true;

        java.util.List<com.joinlivora.backend.streaming.Stream> streams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId);
        if (streams.isEmpty()) {
            throw new ResourceNotFoundException("Active stream not found");
        }
        com.joinlivora.backend.streaming.Stream room = streams.get(0);

        if (!room.isPaid()) {
            return true;
        }

        // Check if already has access (already paid for this session)
        if (hasAccess(userId, creatorUserId)) {
            return true;
        }

        return tokenWalletService.getTotalBalance(userId) >= room.getAdmissionPrice().longValue();
    }

    @Transactional
    public void deductForLivestream(Long userId, Long creatorUserId) {
        if (userId.equals(creatorUserId)) return;

        // Double-check access to avoid double-charging
        if (hasAccess(userId, creatorUserId)) {
            return;
        }

        java.util.List<com.joinlivora.backend.streaming.Stream> streams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId);
        if (streams.isEmpty()) {
            throw new ResourceNotFoundException("Active stream not found");
        }
        com.joinlivora.backend.streaming.Stream room = streams.get(0);

        if (room.isPaid()) {
            spendTokens(userId, room.getAdmissionPrice().longValue(), 
                WalletTransactionType.LIVESTREAM_ADMISSION, creatorUserId.toString());
            
            grantAccess(userId, creatorUserId);
            
            log.info("TOKEN: User {} paid {} tokens to watch creator {}", 
                userId, room.getAdmissionPrice(), creatorUserId);
        }
    }

    public boolean hasAccess(Long userId, Long creatorUserId) {
        String key = ACCESS_KEY_PREFIX + creatorUserId;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, userId.toString()));
    }

    public void grantAccess(Long userId, Long creatorUserId) {
        String key = ACCESS_KEY_PREFIX + creatorUserId;
        redisTemplate.opsForSet().add(key, userId.toString());
    }

    public void clearAccess(Long creatorUserId) {
        String key = ACCESS_KEY_PREFIX + creatorUserId;
        redisTemplate.delete(key);
        log.info("TOKEN: Cleared access list for creator {}", creatorUserId);
    }
}
