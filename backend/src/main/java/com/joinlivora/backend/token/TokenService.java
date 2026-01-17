package com.joinlivora.backend.token;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.streaming.StreamRoom;
import com.joinlivora.backend.streaming.StreamRoomRepository;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenTransactionRepository tokenTransactionRepository;
    private final TokenPackageRepository tokenPackageRepository;
    private final TipRecordRepository tipRecordRepository;
    private final CreatorEarningsRepository creatorEarningsRepository;
    private final StreamRoomRepository streamRoomRepository;
    private final AnalyticsEventPublisher analyticsEventPublisher;

    private static final double PLATFORM_FEE_PERCENT = 0.30;

    public List<TokenPackage> getActivePackages() {
        return tokenPackageRepository.findAllByActiveTrue();
    }

    public TokenBalance getBalance(User user) {
        return tokenBalanceRepository.findByUser(user)
                .orElseGet(() -> tokenBalanceRepository.save(
                        TokenBalance.builder()
                                .user(user)
                                .balance(0)
                                .build()
                ));
    }

    @Transactional
    public void creditTokens(User user, long amount, String reference) {
        TokenBalance balance = getBalance(user);
        balance.setBalance(balance.getBalance() + amount);
        tokenBalanceRepository.save(balance);

        tokenTransactionRepository.save(TokenTransaction.builder()
                .user(user)
                .amount(amount)
                .reason(TransactionReason.PURCHASE)
                .reference(reference)
                .build());
        
        log.info("TOKEN: Credited {} tokens to user {}. Ref: {}", amount, user.getEmail(), reference);
        
        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.PAYMENT_SUCCEEDED, // Reuse or create new type
                user,
                Map.of("tokenAmount", amount, "type", "purchase", "reference", reference)
        );
    }

    @Transactional
    public void sendTip(User viewer, UUID roomId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Tip amount must be positive");
        }

        TokenBalance viewerBalance = getBalance(viewer);
        if (viewerBalance.getBalance() < amount) {
            throw new RuntimeException("Insufficient token balance");
        }

        StreamRoom room = streamRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Stream room not found"));

        if (!room.isLive()) {
            throw new RuntimeException("Cannot tip: Stream is not live");
        }

        User creator = room.getCreator();
        
        // 1. Deduct from viewer
        viewerBalance.setBalance(viewerBalance.getBalance() - amount);
        tokenBalanceRepository.save(viewerBalance);

        tokenTransactionRepository.save(TokenTransaction.builder()
                .user(viewer)
                .amount(-amount)
                .reason(TransactionReason.TIP)
                .reference("Tip to room " + roomId)
                .build());

        // 2. Calculate fees and earnings
        long platformFee = Math.round(amount * PLATFORM_FEE_PERCENT);
        long creatorEarning = amount - platformFee;

        // 3. Credit creator
        CreatorEarnings earnings = creatorEarningsRepository.findByUser(creator)
                .orElseGet(() -> creatorEarningsRepository.save(
                        CreatorEarnings.builder()
                                .user(creator)
                                .build()
                ));
        
        earnings.setTotalEarnedTokens(earnings.getTotalEarnedTokens() + creatorEarning);
        earnings.setAvailableTokens(earnings.getAvailableTokens() + creatorEarning);
        creatorEarningsRepository.save(earnings);

        // 4. Record tip
        TipRecord tipRecord = TipRecord.builder()
                .viewer(viewer)
                .creator(creator)
                .room(room)
                .amount(amount)
                .creatorEarningTokens(creatorEarning)
                .platformFeeTokens(platformFee)
                .build();
        tipRecordRepository.save(tipRecord);

        log.info("TOKEN: Tip of {} tokens from {} to {} in room {}", amount, viewer.getEmail(), creator.getEmail(), roomId);

        // 5. Publish events for analytics and real-time
        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.PAYMENT_SUCCEEDED, // Using this for now
                viewer,
                Map.of(
                        "type", "tip",
                        "amount", amount,
                        "creatorId", creator.getId(),
                        "roomId", roomId,
                        "creatorEarning", creatorEarning,
                        "platformFee", platformFee
                )
        );
    }

    @Transactional
    public void deductTokens(User user, long amount, TransactionReason reason, String reference) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        TokenBalance balance = getBalance(user);
        if (balance.getBalance() < amount) {
            throw new RuntimeException("Insufficient token balance");
        }

        balance.setBalance(balance.getBalance() - amount);
        tokenBalanceRepository.save(balance);

        tokenTransactionRepository.save(TokenTransaction.builder()
                .user(user)
                .amount(-amount)
                .reason(reason)
                .reference(reference)
                .build());

        log.info("TOKEN: Deducted {} tokens from user {} for {}. Ref: {}", amount, user.getEmail(), reason, reference);
    }

    public List<TokenTransaction> getTransactionHistory(User user) {
        return tokenTransactionRepository.findAllByUserOrderByCreatedAtDesc(user);
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
}
