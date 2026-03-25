package com.joinlivora.backend.payout.policy;

import com.joinlivora.backend.common.exception.KycNotApprovedException;
import com.joinlivora.backend.creator.verification.KycAccessService;
import com.joinlivora.backend.exception.BusinessException;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.common.exception.PayoutBlockedException;
import com.joinlivora.backend.payout.freeze.PayoutFreezeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PayoutEligibilityService {

    private final KycAccessService kycAccessService;
    private final PayoutFreezeService payoutFreezeService;
    private final UserRiskStateRepository userRiskStateRepository;

    public PayoutEligibilityService(
            KycAccessService kycAccessService,
            @org.springframework.beans.factory.annotation.Qualifier("payoutFreezeServiceNew") PayoutFreezeService payoutFreezeService,
            UserRiskStateRepository userRiskStateRepository) {
        this.kycAccessService = kycAccessService;
        this.payoutFreezeService = payoutFreezeService;
        this.userRiskStateRepository = userRiskStateRepository;
    }

    public void assertEligibleForPayout(Long creatorId, BigDecimal amount) {
        // 1. Call kycAccessService.assertCreatorCanReceivePayout(creator)
        kycAccessService.assertCreatorCanReceivePayout(creatorId);

        // 2. Check if payout freeze exists and is active → throw PayoutBlockedException
        payoutFreezeService.findActiveFreeze(creatorId)
                .ifPresent(freeze -> {
                    throw new PayoutBlockedException("Active payout freeze: " + freeze.getReason());
                });

        // 3. Check if user risk state is BLOCKED → throw PayoutBlockedException
        userRiskStateRepository.findById(creatorId)
                .ifPresent(riskState -> {
                    if (riskState.isPaymentLocked() || (riskState.getBlockedUntil() != null && riskState.getBlockedUntil().isAfter(Instant.now()))) {
                        throw new PayoutBlockedException("User risk state is BLOCKED");
                    }
                });

        // 4. Check minimum payout threshold (for example 50.00)
        BigDecimal minThreshold = new BigDecimal("50.00");
        if (amount.compareTo(minThreshold) < 0) {
            throw new BusinessException("Payout amount is below minimum threshold of " + minThreshold);
        }
    }
}
