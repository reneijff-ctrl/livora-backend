package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.policy.PayoutEligibilityService;
import com.joinlivora.backend.exception.BusinessException;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.fraud.service.FraudScoreService;
import com.joinlivora.backend.payout.dto.PayoutEligibilityResponseDTO;
import com.joinlivora.backend.payout.dto.PayoutHoldStatusDTO;
import com.joinlivora.backend.payout.dto.PayoutRequestAdminDetailDTO;
import com.joinlivora.backend.payout.dto.PayoutRequestResponseDTO;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserStatus;
import com.stripe.model.Transfer;
import com.stripe.param.TransferCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutRequestService {

    private final PayoutRequestRepository payoutRequestRepository;
    private final PayoutHoldService payoutHoldService;
    private final UserRiskStateRepository userRiskStateRepository;
    private final LegacyCreatorProfileRepository creatorProfileRepository;
    private final CreatorPayoutSettingsRepository creatorPayoutSettingsRepository;
    private final LegacyCreatorStripeAccountRepository creatorStripeAccountRepository;
    private final CreatorBalanceService creatorBalanceService;
    private final CreatorEarningRepository creatorEarningRepository;
    private final FraudScoreService fraudScoreService;
    private final PayoutEligibilityService payoutEligibilityService;
    private final PayoutService payoutService;
    private final com.stripe.StripeClient stripeClient;

    @Transactional(readOnly = true)
    public PayoutEligibilityResponseDTO checkEligibility(User user) {
        List<String> reasons = new ArrayList<>();

        // 1. Check User Role
        if (user.getRole() != Role.CREATOR) {
            reasons.add("User is not a creator");
        }

        // 2. Check User Status
        if (user.getStatus() != UserStatus.ACTIVE) {
            reasons.add("User account is not active (status: " + user.getStatus() + ")");
        }

        // 3. Check payoutsEnabled in User entity
        if (!user.isPayoutsEnabled()) {
            reasons.add("Payouts are disabled for this account");
        }

        // 4. Check Fraud Risk Level
        if (user.getFraudRiskLevel() == FraudRiskLevel.HIGH) {
            reasons.add("Payouts are blocked due to high fraud risk level");
        }

        // 5. Check UserRiskState for payment lock
        userRiskStateRepository.findById(user.getId())
                .ifPresent(riskState -> {
                    if (riskState.isPaymentLocked()) {
                        reasons.add("Payouts are locked due to security reasons");
                    }
                });

        // 6. Check for active payout holds
        if (payoutHoldService.hasActiveHold(user)) {
            reasons.add("An active payout hold exists on this account");
        }

        // 7. Check Creator Profile and Settings
        creatorProfileRepository.findByUser(user).ifPresentOrElse(profile -> {
            // Use canonical creatorId for settings lookup (matches onboarding/webhook bridge)
            UUID canonicalCreatorId = new UUID(0L, profile.getUser().getId());
            creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId).ifPresentOrElse(settings -> {
                if (!settings.isEnabled()) {
                    reasons.add("Payouts are disabled in creator settings");
                }

                // 8. Check Balance vs Minimum Payout Amount
                Map<String, BigDecimal> balances = creatorBalanceService.getAvailableBalance(user);
                BigDecimal availableBalance = balances.getOrDefault("EUR", BigDecimal.ZERO);
                BigDecimal minAmount = settings.getMinimumPayoutAmount();
                if (minAmount != null && availableBalance.compareTo(minAmount) < 0) {
                    reasons.add("Available balance (" + availableBalance + " EUR) is below minimum payout amount (" + minAmount + " EUR)");
                }
            }, () -> reasons.add("Payout settings not found"));
        }, () -> reasons.add("Creator profile not found"));

        // 9. Check Stripe Account Status
        creatorStripeAccountRepository.findByCreatorId(user.getId()).ifPresentOrElse(stripeAccount -> {
            if (!stripeAccount.isPayoutsEnabled()) {
                reasons.add("Stripe payouts are not enabled. Please complete onboarding.");
            }
        }, () -> reasons.add("Stripe account not linked"));

        boolean eligible = reasons.isEmpty();
        log.debug("Payout eligibility check for creator {}: eligible={}, reasons={}", user.getEmail(), eligible, reasons);

        return PayoutEligibilityResponseDTO.builder()
                .eligible(eligible)
                .reasons(reasons)
                .build();
    }

    @Transactional
    public PayoutRequest createPayoutRequest(User user) {
        BigDecimal availableBalance = creatorBalanceService.getAvailableBalance(user).getOrDefault("EUR", BigDecimal.ZERO);
        payoutEligibilityService.assertEligibleForPayout(user.getId(), availableBalance);

        if (availableBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payout amount must be greater than zero");
        }

        // Cross-check against canonical available balance to prevent double-spend across payout flows
        UUID canonicalCreatorId = new UUID(0L, user.getId());
        BigDecimal canonicalAvailable = payoutService.calculateAvailablePayout(canonicalCreatorId);
        log.info("PAYOUT_DEBUG: createPayoutRequest availableBalance={}, canonicalAvailable={}, user={}", availableBalance, canonicalAvailable, user.getId());

        if (canonicalAvailable.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("No available balance after accounting for existing payouts");
        }

        BigDecimal safeAmount = availableBalance.min(canonicalAvailable);
        log.info("PAYOUT_DEBUG: createPayoutRequest safeAmount={} (min of {} and {})", safeAmount, availableBalance, canonicalAvailable);

        PayoutEligibilityResponseDTO eligibility = checkEligibility(user);
        if (!eligibility.isEligible()) {
            throw new BusinessException(eligibility.getReasons().get(0));
        }

        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Creator profile not found"));

        PayoutRequest request = PayoutRequest.builder()
                .creatorId(profile.getId())
                .amount(safeAmount)
                .currency("EUR")
                .status(PayoutRequestStatus.PENDING)
                .build();

        request = payoutRequestRepository.save(request);

        List<CreatorEarning> earnings = creatorEarningRepository.findAvailableEarningsByCreator(user);
        List<CreatorEarning> toLock = new java.util.ArrayList<>();
        for (CreatorEarning earning : earnings) {
            if ("EUR".equals(earning.getCurrency())) {
                earning.setLocked(true);
                earning.setPayoutRequest(request);
                toLock.add(earning);
            }
        }
        creatorEarningRepository.saveAll(toLock);

        log.info("Creating payout request for creator {}: amount={} EUR, locked {} earnings",
                user.getEmail(), availableBalance, toLock.size());
        return request;
    }

    @Transactional(readOnly = true)
    public List<PayoutRequest> getPayoutRequestsByUser(User user) {
        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Creator profile not found"));
        return payoutRequestRepository.findAllByCreatorIdOrderByCreatedAtDesc(profile.getId());
    }

    @Transactional(readOnly = true)
    public List<PayoutRequest> getAllPayoutRequests() {
        return payoutRequestRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<PayoutRequest> getPayoutRequestsByStatus(PayoutRequestStatus status) {
        return payoutRequestRepository.findAllByStatus(status);
    }

    @Transactional(readOnly = true)
    public PayoutRequestAdminDetailDTO getPayoutRequestAdminDetail(UUID id) {
        PayoutRequest request = payoutRequestRepository.findById(id)
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("Payout request not found"));

        LegacyCreatorProfile profile = creatorProfileRepository.findById(request.getCreatorId())
                .orElseThrow(() -> new BusinessException("Creator profile not found"));
        User creator = profile.getUser();
        UUID creatorUuid = new UUID(0L, creator.getId());

        int fraudScore = fraudScoreService.calculateFraudScore(creatorUuid);
        int trustScore = creator.getTrustScore();

        // Get Stripe status
        boolean stripeReady = creatorStripeAccountRepository.findByCreatorId(creator.getId())
                .map(LegacyCreatorStripeAccount::isPayoutsEnabled)
                .orElse(false);

        // Get payout holds
        PayoutHoldStatusDTO holdStatus = payoutHoldService.getPayoutHoldStatus(creator);
        List<PayoutHoldStatusDTO> activeHolds = new ArrayList<>();
        if (holdStatus.getHoldLevel() != com.joinlivora.backend.payout.HoldLevel.NONE) {
            activeHolds.add(holdStatus);
        }

        return PayoutRequestAdminDetailDTO.builder()
                .id(request.getId())
                .creatorEmail(creator.getEmail())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(request.getStatus())
                .requestedAt(request.getCreatedAt())
                .fraudScore(fraudScore)
                .trustScore(trustScore)
                .payoutHolds(activeHolds)
                .stripeReady(stripeReady)
                .rejectionReason(request.getRejectionReason())
                .build();
    }

    @Transactional
    public PayoutRequest approvePayoutRequest(UUID id) {
        PayoutRequest request = payoutRequestRepository.findById(id)
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("Payout request not found"));

        if (request.getStatus() != PayoutRequestStatus.PENDING) {
            throw new BusinessException("Only PENDING payout requests can be approved");
        }

        LegacyCreatorProfile profile = creatorProfileRepository.findById(request.getCreatorId())
                .orElseThrow(() -> new BusinessException("Creator profile not found"));
        User user = profile.getUser();

        // 1. Get creator Stripe account
        LegacyCreatorStripeAccount stripeAccount = creatorStripeAccountRepository.findByCreatorId(user.getId())
                .orElseThrow(() -> new BusinessException("Stripe account not found for creator"));

        if (!stripeAccount.isPayoutsEnabled()) {
            throw new BusinessException("Stripe payouts are not enabled for this creator");
        }

        try {
            // 2. Trigger Stripe transfer
            TransferCreateParams params = TransferCreateParams.builder()
                    .setAmount(request.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
                    .setCurrency(request.getCurrency().toLowerCase())
                    .setDestination(stripeAccount.getStripeAccountId())
                    .putMetadata("payoutRequestId", request.getId().toString())
                    .putMetadata("creator", profile.getId().toString())
                    .build();

            Transfer transfer = stripeClient.transfers().create(params);

            // 3. Update status to APPROVED (awaiting Stripe webhook confirmation)
            request.setStatus(PayoutRequestStatus.APPROVED);
            request.setStripeTransferId(transfer.getId());
            request.setUpdatedAt(java.time.Instant.now());

            log.info("PAYOUT_DEBUG: Payout request {} approved and set to APPROVED (awaiting webhook). Transfer ID: {}", id, transfer.getId());
            return payoutRequestRepository.save(request);

        } catch (Exception e) {
            log.error("Failed to process Stripe transfer for payout request {}. Status set to FAILED.", id, e);
            
            request.setStatus(PayoutRequestStatus.FAILED);
            request.setUpdatedAt(java.time.Instant.now());
            request.setRejectionReason("Stripe transfer failed: " + e.getMessage());
            
            unlockEarnings(request);
            
            payoutRequestRepository.save(request);
            
            throw new BusinessException("Stripe transfer failed: " + e.getMessage());
        }
    }

    @Transactional
    public PayoutRequest rejectPayoutRequest(UUID id, String reason) {
        PayoutRequest request = payoutRequestRepository.findById(id)
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("Payout request not found"));

        if (request.getStatus() != PayoutRequestStatus.PENDING) {
            throw new BusinessException("Only PENDING payout requests can be rejected");
        }

        request.setStatus(PayoutRequestStatus.REJECTED);
        request.setUpdatedAt(java.time.Instant.now());
        request.setRejectionReason(reason);

        unlockEarnings(request);

        log.info("Payout request {} rejected. Reason: {}", id, reason);
        return payoutRequestRepository.save(request);
    }

    private void unlockEarnings(PayoutRequest request) {
        List<CreatorEarning> earnings = creatorEarningRepository.findAllByPayoutRequest(request);
        for (CreatorEarning earning : earnings) {
            earning.setLocked(false);
            earning.setPayoutRequest(null);
        }
        creatorEarningRepository.saveAll(earnings);
    }

    public PayoutRequestResponseDTO mapToResponseDTO(PayoutRequest request) {
        return PayoutRequestResponseDTO.builder()
                .id(request.getId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(request.getStatus())
                .requestedAt(request.getCreatedAt())
                .processedAt(request.getUpdatedAt())
                .rejectionReason(request.getRejectionReason())
                .build();
    }
}
