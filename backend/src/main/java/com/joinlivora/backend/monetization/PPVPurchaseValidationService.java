package com.joinlivora.backend.monetization;

import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("ppvPurchaseValidationService")
@RequiredArgsConstructor
@Slf4j
public class PPVPurchaseValidationService {

    private final PpvPurchaseRepository ppvPurchaseRepository;

    /**
     * Checks if a creator has successfully purchased specific PPV content.
     *
     * @param user    The creator to check
     * @param content The PPV content to check
     * @return true if a COMPLETED purchase record exists
     */
    public boolean hasPurchased(User user, PpvContent content) {
        if (user == null || content == null) {
            return false;
        }
        return ppvPurchaseRepository.findByPpvContentAndUserAndStatus(content, user, PpvPurchaseStatus.PAID).isPresent();
    }

    /**
     * Checks if a creator has successfully purchased specific PPV content by its ID.
     *
     * @param userId       The ID of the creator to check
     * @param ppvContentId The ID of the PPV content to check
     * @return true if a COMPLETED purchase record exists
     */
    public boolean hasPurchased(Long userId, UUID ppvContentId) {
        if (userId == null || ppvContentId == null) {
            return false;
        }
        return ppvPurchaseRepository.existsByPpvContent_IdAndUser_IdAndStatus(ppvContentId, userId, PpvPurchaseStatus.PAID);
    }
}
