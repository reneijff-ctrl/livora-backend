package com.joinlivora.backend.streaming;

import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.wallet.WalletTransactionType;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.payout.CreatorEarningsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final TokenService tokenService;
    private final CreatorEarningsService creatorEarningsService;

    public List<Badge> getAllBadges() {
        return badgeRepository.findAll();
    }

    @Transactional
    public UserBadge purchaseBadge(User user, UUID badgeId) {
        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> new RuntimeException("Badge not found"));

        // 1. Deduct tokens
        tokenService.deductTokens(user, badge.getTokenCost(), WalletTransactionType.BADGE, "Purchase badge: " + badge.getName());

        // 2. Record creatorUserId earning (simplified: platform takes all or split if it's creatorUserId-specific badges)
        // For now, let's assume badges are platform-wide but revenue goes to creatorUserId if it's a specific creatorUserId badge
        // Here we just record it as a general transaction if not specified.
        // creatorEarningsService.recordBadgeEarning(...)

        // 3. Assign badge
        Instant expiresAt = null;
        if (badge.getDurationDays() != null) {
            expiresAt = Instant.now().plus(java.time.Duration.ofDays(badge.getDurationDays()));
        }

        UserBadge userBadge = UserBadge.builder()
                .user(user)
                .badge(badge)
                .expiresAt(expiresAt)
                .build();

        return userBadgeRepository.save(userBadge);
    }

    public List<UserBadge> getUserBadges(User user) {
        return userBadgeRepository.findAllByUser(user);
    }
}
