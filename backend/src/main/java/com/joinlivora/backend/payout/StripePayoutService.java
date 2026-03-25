package com.joinlivora.backend.payout;

import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.stripe.StripeClient;
import com.stripe.model.Transfer;
import com.stripe.param.TransferCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service("stripePayoutService")
@RequiredArgsConstructor
@Slf4j
public class StripePayoutService {

    private final CreatorPayoutRepository payoutRepository;
    private final CreatorEarningRepository earningRepository;
    private final CreatorPayoutSettingsRepository settingsRepository;
    private final StripeClient stripeClient;
    private final PayoutAuditService payoutAuditService;

    @Value("${payments.stripe.enabled:true}")
    private boolean stripeEnabled;

    @Transactional
    public void triggerPayout(UUID payoutId) {
        log.info("STRIPE_PAYOUT: Triggering payout {}", payoutId);

        // 1. Find PENDING payout
        CreatorPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found: " + payoutId));

        if (payout.getStatus() != PayoutStatus.PENDING) {
            throw new IllegalStateException("Payout " + payoutId + " is not in PENDING status. Current status: " + payout.getStatus());
        }

        if (!stripeEnabled) {
            log.info("STRIPE_PAYOUT: Stripe disabled, failing payout {} gracefully", payoutId);
            PayoutStatus oldStatus = payout.getStatus();
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason("Stripe disabled");
            payoutRepository.save(payout);
            payoutAuditService.logStatusChange(payout.getId(), oldStatus, PayoutStatus.FAILED, PayoutActorType.SYSTEM, null, "Stripe disabled" );
            unlockEarnings(payout);
            return;
        }

        UUID creatorId = payout.getCreatorId();
        BigDecimal amount = payout.getAmount();
        String currency = payout.getCurrency();

        // 2. Validate Stripe account connected
        CreatorPayoutSettings settings = settingsRepository.findByCreatorId(creatorId)
                .orElseThrow(() -> new IllegalStateException("Payout settings not found for creator " + creatorId));

        if (settings.getStripeAccountId() == null || settings.getStripeAccountId().isBlank()) {
            log.error("STRIPE_PAYOUT: Stripe account not connected for creator {}", creatorId);
            PayoutStatus oldStatus = payout.getStatus();
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason("Stripe account not connected");
            payoutRepository.save(payout);
            payoutAuditService.logStatusChange(payout.getId(), oldStatus, PayoutStatus.FAILED, PayoutActorType.SYSTEM, null, "Payout failed: Stripe account not connected");
            unlockEarnings(payout);
            throw new IllegalStateException("Stripe account not connected for creator " + creatorId);
        }

        // 3. Mark payout as PROCESSING
        PayoutStatus preProcessingStatus = payout.getStatus();
        payout.setStatus(PayoutStatus.PROCESSING);
        payoutRepository.save(payout);
        payoutAuditService.logStatusChange(payout.getId(), preProcessingStatus, PayoutStatus.PROCESSING, PayoutActorType.SYSTEM, null, "Payout processing started");

        try {
            // 4. Create Stripe transfer
            TransferCreateParams params = TransferCreateParams.builder()
                    .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue()) // Amount in cents
                    .setCurrency(currency.toLowerCase())
                    .setDestination(settings.getStripeAccountId())
                    .putMetadata("payoutId", payout.getId().toString())
                    .putMetadata("creator", creatorId.toString())
                    .build();

            Transfer transfer = stripeClient.transfers().create(params);

            // 5. On success mark COMPLETED
            PayoutStatus oldStatus = payout.getStatus();
            payout.setStripeTransferId(transfer.getId());
            payout.setStatus(PayoutStatus.COMPLETED);
            payout.setCompletedAt(java.time.Instant.now());
            payoutRepository.save(payout);
            payoutAuditService.logStatusChange(payout.getId(), oldStatus, PayoutStatus.COMPLETED, PayoutActorType.STRIPE, null, "Stripe transfer created: " + transfer.getId());
            log.info("STRIPE_PAYOUT: Payout {} COMPLETED successfully with transfer ID {}", payout.getId(), transfer.getId());

        } catch (Exception e) {
            log.error("STRIPE_PAYOUT: Payout {} FAILED", payout.getId(), e);
            // 6. On failure mark FAILED and unlock earnings
            PayoutStatus oldStatus = payout.getStatus();
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason(e.getMessage());
            payoutRepository.save(payout);
            payoutAuditService.logStatusChange(payout.getId(), oldStatus, PayoutStatus.FAILED, PayoutActorType.SYSTEM, null, "Stripe transfer failed: " + e.getMessage());

            unlockEarnings(payout);
        }
    }

    private void unlockEarnings(CreatorPayout payout) {
        List<CreatorEarning> earnings = earningRepository.findAllByPayout(payout);
        for (CreatorEarning earning : earnings) {
            earning.setLocked(false);
            // We keep the payout referenceId so we can re-lock them later if needed
        }
        earningRepository.saveAll(earnings);
        log.info("STRIPE_PAYOUT: Unlocked {} earnings for failed payout {}", earnings.size(), payout.getId());
    }
}
