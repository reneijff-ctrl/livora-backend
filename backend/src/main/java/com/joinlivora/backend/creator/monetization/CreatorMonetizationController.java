package com.joinlivora.backend.creator.monetization;

import com.joinlivora.backend.creator.dto.CreatorMonetizationResponse;
import com.joinlivora.backend.creator.model.CreatorMonetization;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.payout.dto.CreatorEarningsSummary;
import com.joinlivora.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/creator/monetization")
@RequiredArgsConstructor
public class CreatorMonetizationController {

    private final CreatorMonetizationService monetizationService;
    private final CreatorProfileService profileService;
    private final CreatorEarningsService earningsService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorMonetizationResponse> getMonetization(@AuthenticationPrincipal UserPrincipal principal) {
        CreatorProfile profile = profileService.getCreatorByUserId(principal.getUserId());
        CreatorMonetization monetization = monetizationService.getOrCreateForCreator(profile);
        CreatorEarningsSummary earnings = earningsService.getEarningsSummary(profile.getUser());

        return ResponseEntity.ok(CreatorMonetizationResponse.builder()
                .subscriptionPrice(monetization.getSubscriptionPrice())
                .tipEnabled(monetization.isTipEnabled())
                .balance(earnings.getAvailableBalance())
                .pendingBalance(earnings.getPendingBalance())
                .lifetimeEarnings(earnings.getTotalEarned())
                .build());
    }
}
