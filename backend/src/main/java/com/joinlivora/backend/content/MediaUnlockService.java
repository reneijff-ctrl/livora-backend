package com.joinlivora.backend.content;

import com.joinlivora.backend.content.dto.UnlockResponse;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.token.TokenWalletService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.wallet.WalletTransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaUnlockService {

    private final ContentRepository contentRepository;
    private final ContentUnlockRepository contentUnlockRepository;
    private final TokenWalletService tokenWalletService;
    private final CreatorEarningsService creatorEarningsService;

    @Transactional
    public UnlockResponse unlockMedia(User user, UUID contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("Media not found"));

        if (contentUnlockRepository.existsByUserAndContent(user, content)) {
            return UnlockResponse.builder()
                    .unlocked(true)
                    .remainingTokens(tokenWalletService.getTotalBalance(user.getId()))
                    .build();
        }

        long price = content.getUnlockPriceTokens().longValue();
        
        // 1. Deduct tokens from buyer using pessimistic locking
        tokenWalletService.deductTokens(user.getId(), price, WalletTransactionType.MEDIA_UNLOCK, contentId.toString());

        // 2. Credit tokens to creator earnings
        creatorEarningsService.recordPPVTokenEarning(user, content.getCreator(), price, contentId);

        ContentUnlock unlock = ContentUnlock.builder()
                .user(user)
                .content(content)
                .build();
        
        contentUnlockRepository.save(unlock);
        
        log.info("MEDIA: User {} unlocked content {} for {} tokens. Creator: {}", user.getId(), contentId, price, content.getCreator().getId());

        return UnlockResponse.builder()
                .unlocked(true)
                .remainingTokens(tokenWalletService.getTotalBalance(user.getId()))
                .build();
    }

    public boolean isUnlockedByUser(Long userId, UUID contentId) {
        return contentUnlockRepository.existsByUserIdAndContentId(userId, contentId);
    }
}
