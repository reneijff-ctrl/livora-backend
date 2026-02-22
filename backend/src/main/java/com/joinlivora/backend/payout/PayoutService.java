package com.joinlivora.backend.payout;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.creator.verification.KycAccessService;
import com.joinlivora.backend.exception.PayoutRestrictedException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.RiskProfile;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.fraud.service.RiskDecisionEngine;
import com.joinlivora.backend.fraud.service.RiskProfileService;
import com.joinlivora.backend.fraud.FraudFlag;
import com.joinlivora.backend.fraud.FraudFlagSource;
import com.joinlivora.backend.fraud.repository.FraudFlagRepository;
import com.joinlivora.backend.fraud.service.FraudScoreService;
import com.joinlivora.backend.payout.dto.PayoutFrequency;
import com.joinlivora.backend.payout.dto.PayoutLimit;
import com.joinlivora.backend.token.CreatorEarnings;
import com.joinlivora.backend.token.CreatorEarningsRepository;
import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.user.User;
import com.stripe.StripeClient;
import com.stripe.model.Transfer;
import com.stripe.param.TransferCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service("payoutService")
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final CreatorPayoutRepository creatorPayoutRepository;
    private final CreatorPayoutSettingsRepository creatorPayoutSettingsRepository;
    private final StripeAccountRepository stripeAccountRepository;
    private final TokenService tokenService;
    private final StripeClient stripeClient;
    private final CreatorEarningRepository creatorEarningRepository;
    private final CreatorEarningsRepository creatorEarningsRepository;
    private final com.joinlivora.backend.user.UserRepository userRepository;
    private final com.joinlivora.backend.payment.AutoFreezePolicyService autoFreezePolicyService;
    private final PayoutAbuseDetectionService payoutAbuseDetectionService;
    private final RiskProfileService riskProfileService;
    private final RiskDecisionEngine riskDecisionEngine;
    private final PayoutLimitPolicy payoutLimitPolicy;
    private final CreatorPayoutStateRepository creatorPayoutStateRepository;
    private final PayoutPolicyAuditService payoutPolicyAuditService;
    private final StripePayoutAdapter stripePayoutAdapter;
    private final PayoutHoldAutomationService payoutHoldAutomationService;
    private final FraudScoreService fraudScoreService;
    private final FraudFlagRepository fraudFlagRepository;
    private final AuditService auditService;

    private static final long MIN_PAYOUT_TOKENS = 5000; // e.g. 5000 tokens = 50 EUR


    private final PayoutAuditService payoutAuditService;
    private final KycAccessService kycAccessService;

    public List<Payout> getPayoutHistory(User user) {
        return payoutRepository.findAllByUserOrderByCreatedAtDesc(user);
    }

    /**
     * Calculates the available payout amount for a creator.
     * Rules:
     * - Sum net earnings (converted to EUR)
     * - Subtract already paid payouts
     * - Exclude pending transactions (subtract pending payouts)
     */
    public BigDecimal calculateAvailablePayout(UUID creatorId) {
        CreatorEarnings earnings = creatorEarningsRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator earnings record not found for ID: " + creatorId));
        User user = earnings.getUser();

        // 1. Sum net earnings
        BigDecimal totalNetTokens = creatorEarningRepository.sumTotalNetTokensByCreator(user);
        BigDecimal totalNetRevenue = creatorEarningRepository.sumTotalNetRevenueByCreator(user);

        if (totalNetTokens == null) totalNetTokens = BigDecimal.ZERO;
        if (totalNetRevenue == null) totalNetRevenue = BigDecimal.ZERO;

        BigDecimal totalEarningsEur = totalNetRevenue.add(
                totalNetTokens.multiply(CreatorEarningsService.TOKEN_TO_EUR_RATE)
        );

        // 2. Subtract already paid payouts
        BigDecimal paidAmount = payoutRepository.sumEurAmountByUserAndStatus(user, PayoutStatus.COMPLETED);
        if (paidAmount == null) paidAmount = BigDecimal.ZERO;

        // 3. Exclude pending transactions (subtract pending payouts)
        BigDecimal pendingAmount = payoutRepository.sumEurAmountByUserAndStatus(user, PayoutStatus.PENDING);
        if (pendingAmount == null) pendingAmount = BigDecimal.ZERO;

        return totalEarningsEur.subtract(paidAmount).subtract(pendingAmount);
    }

    /**
     * Executes a payout for a creator.
     * Checks minimum amount, creates Stripe transfer, updates status, and persists the record.
     */
    @Transactional
    public CreatorPayout executePayout(UUID creatorId, BigDecimal amount, String currency) throws Exception {
        CreatorPayoutSettings settings = creatorPayoutSettingsRepository.findByCreatorId(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout settings not found for creator: " + creatorId));

        // Use lock to prevent concurrent payouts for the same creator
        CreatorEarnings earnings = creatorEarningsRepository.findByIdWithLock(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator earnings not found: " + creatorId));
        User user = earnings.getUser();

        kycAccessService.assertCreatorCanReceivePayout(user.getId());

        // Re-validate available balance inside the transaction
        BigDecimal available = calculateAvailablePayout(creatorId);
        if (amount.compareTo(available) > 0) {
            throw new IllegalStateException(String.format("Insufficient funds. Available: %s %s, Requested: %s %s", 
                    available, currency, amount, currency));
        }

        autoFreezePolicyService.validateUserStatus(user);
        payoutAbuseDetectionService.detect(user, amount);
        payoutHoldAutomationService.evaluateAndApplyHold(user);
        validatePayoutLimit(creatorId, amount);
        stripePayoutAdapter.validateNoActiveHold(new UUID(0L, user.getId()), RiskSubjectType.CREATOR);

        // Fraud score check
        int fraudScore = fraudScoreService.calculateFraudScore(new UUID(0L, user.getId()));
        if (fraudScore >= 70) {
            log.warn("PAYOUT: Payout blocked for creator {} due to high fraud score: {}", creatorId, fraudScore);
            fraudFlagRepository.save(FraudFlag.builder()
                    .userId(new UUID(0L, user.getId()))
                    .source(FraudFlagSource.SYSTEM)
                    .score(fraudScore)
                    .reason("Payout blocked due to high fraud score: " + fraudScore)
                    .build());

            CreatorPayout blockedPayout = CreatorPayout.builder()
                    .creatorId(creatorId)
                    .amount(amount)
                    .currency(currency)
                    .status(PayoutStatus.FAILED)
                    .failureReason("High fraud score: " + fraudScore)
                    .build();
            blockedPayout = creatorPayoutRepository.save(blockedPayout);
            payoutAuditService.logStatusChange(blockedPayout.getId(), null, PayoutStatus.FAILED, PayoutActorType.SYSTEM, null, "Payout blocked due to high fraud score: " + fraudScore);
            return blockedPayout;
        }

        if (!settings.isEnabled()) {
            throw new IllegalStateException("Payouts are disabled for this creator");
        }

        if (settings.getMinimumPayoutAmount() != null && amount.compareTo(settings.getMinimumPayoutAmount()) < 0) {
            throw new IllegalArgumentException("Payout amount " + amount + " is below minimum " + settings.getMinimumPayoutAmount());
        }

        if (settings.getStripeAccountId() == null) {
            throw new IllegalStateException("No Stripe account associated with this creator");
        }

        // 1. Create and persist payout record as PENDING
        CreatorPayout payout = CreatorPayout.builder()
                .creatorId(creatorId)
                .amount(amount)
                .currency(currency)
                .status(PayoutStatus.PENDING)
                .build();
        payout = creatorPayoutRepository.save(payout);
        payoutAuditService.logStatusChange(payout.getId(), null, PayoutStatus.PENDING, PayoutActorType.SYSTEM, null, "Payout request received");

        auditService.logEvent(
                null,
                AuditService.PAYOUT_REQUESTED,
                "PAYOUT",
                payout.getId(),
                Map.of("amount", amount, "currency", currency, "creator", creatorId),
                null,
                null
        );

        try {
            // 2. Create Stripe Transfer
            TransferCreateParams params = TransferCreateParams.builder()
                    .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue()) // Amount in cents
                    .setCurrency(currency.toLowerCase())
                    .setDestination(settings.getStripeAccountId())
                    .putMetadata("payoutId", payout.getId().toString())
                    .putMetadata("creator", creatorId.toString())
                    .build();

            Transfer transfer = stripeClient.transfers().create(params);

            // 3. Update payout status to COMPLETED
            PayoutStatus oldStatus = payout.getStatus();
            payout.setStripeTransferId(transfer.getId());
            payout.setStatus(PayoutStatus.COMPLETED);
            payout.setCompletedAt(java.time.Instant.now());
            payoutAuditService.logStatusChange(payout.getId(), oldStatus, PayoutStatus.COMPLETED, PayoutActorType.SYSTEM, null, "Stripe transfer created: " + transfer.getId());

            auditService.logEvent(
                    null,
                    AuditService.PAYOUT_EXECUTED,
                    "PAYOUT",
                    payout.getId(),
                    Map.of(
                            "amount", amount,
                            "currency", currency,
                            "creator", creatorId,
                            "stripeTransferId", transfer.getId()
                    ),
                    null,
                    null
            );

            log.info("PAYOUT: Successfully executed payout {} for creator {}", payout.getId(), creatorId);
        } catch (Exception e) {
            log.error("PAYOUT: Failed to execute payout {} for creator {}", payout.getId(), creatorId, e);
            // 4. Update status to FAILED on error
            PayoutStatus oldStatus = payout.getStatus();
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason(e.getMessage());
            payoutAuditService.logStatusChange(payout.getId(), oldStatus, PayoutStatus.FAILED, PayoutActorType.SYSTEM, null, "Payout execution failed: " + e.getMessage());
            throw e;
        } finally {
            creatorPayoutRepository.save(payout);
        }

        return payout;
    }

    @Transactional
    public CreatorPayout requestPayout(UUID creatorId, BigDecimal amount, String currency) {
        CreatorEarnings earnings = creatorEarningsRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator earnings record not found for ID: " + creatorId));

        kycAccessService.assertCreatorCanReceivePayout(earnings.getUser().getId());

        User user = earnings.getUser();

        // 1. Create and persist payout record as PENDING
        CreatorPayout payout = CreatorPayout.builder()
                .creatorId(creatorId)
                .amount(amount)
                .currency(currency)
                .status(PayoutStatus.PENDING)
                .build();
        payout = creatorPayoutRepository.save(payout);
        payoutAuditService.logStatusChange(payout.getId(), null, PayoutStatus.PENDING, PayoutActorType.SYSTEM, null, "Payout request received");

        // 2. Lock related earnings
        List<CreatorEarning> availableEarnings = creatorEarningRepository.findAvailableEarningsByCreator(user);
        for (CreatorEarning earning : availableEarnings) {
            earning.setLocked(true);
            earning.setPayout(payout);
        }
        creatorEarningRepository.saveAll(availableEarnings);

        auditService.logEvent(
                null,
                AuditService.PAYOUT_REQUESTED,
                "PAYOUT",
                payout.getId(),
                Map.of("amount", amount, "currency", currency, "creator", creatorId),
                null,
                null
        );

        return payout;
    }

    private void validatePayoutLimit(UUID creatorId, BigDecimal amount) {
        RiskProfile riskProfile = riskProfileService.generateRiskProfile(creatorId);
        RiskDecisionResult decisionResult = riskDecisionEngine.evaluate(RiskSubjectType.CREATOR, riskProfile);
        
        PayoutLimit limit = payoutLimitPolicy.getLimit(riskProfile.getRiskScore());

        CreatorPayoutState state = creatorPayoutStateRepository.findByCreatorId(creatorId)
                .orElse(CreatorPayoutState.builder()
                        .creatorId(creatorId)
                        .build());

        if (state.isManualOverride()) {
            log.info("PAYOUT: Manual override active for creator {}. Skipping auto-update of payout limits.", creatorId);
        } else {
            // Update state based on latest risk evaluation
            state.setCurrentLimit(limit.getMaxPayoutAmount());
            state.setFrequency(limit.getPayoutFrequency());
            state.setStatus(mapToPayoutStateStatus(limit.getPayoutFrequency()));
            creatorPayoutStateRepository.save(state);

            payoutPolicyAuditService.logAutoDecision(creatorId, riskProfile.getRiskScore(), limit, decisionResult.getExplanationId());
        }

        if (state.getStatus() == PayoutStateStatus.PAUSED) {
            throw new PayoutRestrictedException("Payouts are paused for your account. Reason: " + limit.getReason());
        }

        if (state.getStatus() == PayoutStateStatus.LIMITED) {
            java.time.Instant since = java.time.Instant.now();
            if (state.getFrequency() == PayoutFrequency.DAILY) {
                since = since.minus(1, java.time.temporal.ChronoUnit.DAYS);
            } else if (state.getFrequency() == PayoutFrequency.WEEKLY) {
                since = since.minus(7, java.time.temporal.ChronoUnit.DAYS);
            }

            BigDecimal paidInPeriod = creatorPayoutRepository.sumPaidAmountByCreatorIdAndCreatedAtAfter(creatorId, since);
            if (paidInPeriod == null) paidInPeriod = BigDecimal.ZERO;

            if (paidInPeriod.add(amount).compareTo(state.getCurrentLimit()) > 0) {
                throw new PayoutRestrictedException(String.format(
                        "Payout limit exceeded. Current limit: %s EUR. Already paid in this period: %s. Requested: %s. Reason: %s",
                        state.getCurrentLimit(), paidInPeriod, amount, limit.getReason()));
            }
        }
    }

    private PayoutStateStatus mapToPayoutStateStatus(PayoutFrequency frequency) {
        return switch (frequency) {
            case NO_LIMIT -> PayoutStateStatus.ACTIVE;
            case DAILY, WEEKLY -> PayoutStateStatus.LIMITED;
            case PAUSED -> PayoutStateStatus.PAUSED;
        };
    }
}
