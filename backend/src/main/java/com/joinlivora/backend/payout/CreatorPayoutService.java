package com.joinlivora.backend.payout;

import com.joinlivora.backend.exception.BusinessException;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.payout.dto.CreatorPayoutResponseDTO;
import com.joinlivora.backend.payout.dto.PayoutStatusDTO;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorPayoutService {

    private final PayoutService payoutService;
    private final PayoutHoldService payoutHoldService;
    private final LegacyCreatorProfileRepository creatorProfileRepository;
    private final CreatorPayoutSettingsRepository creatorPayoutSettingsRepository;
    private final UserRiskStateRepository userRiskStateRepository;
    private final CreatorPayoutRepository creatorPayoutRepository;
    private final LegacyCreatorStripeAccountRepository creatorStripeAccountRepository;

    public List<CreatorPayoutResponseDTO> getCreatorPayouts(User user) {
        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(user)
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("Creator profile not found for creator: " + user.getEmail()));

        UUID creatorId = new UUID(0L, user.getId());
        log.info("PAYOUT_DEBUG: getCreatorPayouts using canonical creatorId={} for user={}", creatorId, user.getId());
        return creatorPayoutRepository.findAllByCreatorIdOrderByCreatedAtDesc(creatorId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    private CreatorPayoutResponseDTO mapToResponseDTO(CreatorPayout payout) {
        String status = payout.getStatus().name();
        if (payout.getStatus() == PayoutStatus.PENDING) {
            status = "PENDING";
        }

        return CreatorPayoutResponseDTO.builder()
                .id(payout.getId())
                .amount(payout.getAmount())
                .currency(payout.getCurrency())
                .status(status)
                .createdAt(payout.getCreatedAt())
                .completedAt(payout.getCompletedAt())
                .failureReason(payout.getFailureReason())
                .build();
    }

    public PayoutStatusDTO getPayoutStatus(User user) {
        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(user)
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("Creator profile not found for creator: " + user.getEmail()));

        UUID creatorId = new UUID(0L, user.getId());
        log.info("PAYOUT_DEBUG: getPayoutStatus using canonical creatorId={} for user={}", creatorId, user.getId());
        BigDecimal availableBalance = payoutService.calculateAvailablePayout(creatorId);
        
        boolean hasActiveHold = payoutHoldService.hasActiveHold(user);
        
        // Also check UserRiskState for payment lock
        boolean paymentLocked = userRiskStateRepository.findById(user.getId())
                .map(com.joinlivora.backend.fraud.model.UserRiskState::isPaymentLocked)
                .orElse(false);
        
        hasActiveHold = hasActiveHold || paymentLocked;
        
        CreatorPayoutSettings settings = creatorPayoutSettingsRepository.findByCreatorId(creatorId).orElse(null);
        boolean settingsEnabled = settings != null && settings.isEnabled();

        // nextPayoutDate is tomorrow at 03:00 AM UTC
        Instant nextPayoutDate = calculateNextPayoutDate();

        return PayoutStatusDTO.builder()
                .payoutsEnabled(user.isPayoutsEnabled() && settingsEnabled && !paymentLocked)
                .hasActivePayoutHold(hasActiveHold)
                .fraudRiskLevel(user.getFraudRiskLevel())
                .availableBalance(availableBalance)
                .nextPayoutDate(nextPayoutDate)
                .build();
    }

    @Transactional
    public CreatorPayout requestPayout(User user) throws Exception {
        PayoutStatusDTO status = getPayoutStatus(user);
        
        if (!status.isPayoutsEnabled()) {
            throw new IllegalStateException("Payouts are not enabled for this account");
        }

        // Verify Stripe account status
        creatorStripeAccountRepository.findByCreatorId(user.getId())
                .ifPresentOrElse(stripeAccount -> {
                    if (!stripeAccount.isPayoutsEnabled()) {
                        throw new BusinessException("Stripe onboarding incomplete");
                    }
                }, () -> {
                    throw new BusinessException("Stripe onboarding incomplete");
                });
        
        if (status.getFraudRiskLevel() == FraudRiskLevel.HIGH) {
            throw new AccessDeniedException("Payouts are blocked due to high fraud risk level");
        }
        
        if (status.isHasActivePayoutHold()) {
            throw new IllegalStateException("An active payout hold exists on this account");
        }
        
        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(user)
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("Creator profile not found"));
        
        UUID creatorId = new UUID(0L, user.getId());
        log.info("PAYOUT_DEBUG: requestPayout using canonical creatorId={} for user={}", creatorId, user.getId());
        CreatorPayoutSettings settings = creatorPayoutSettingsRepository.findByCreatorId(creatorId)
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("Payout settings not found"));
        
        BigDecimal minAmount = settings.getMinimumPayoutAmount();
        if (minAmount == null) {
            minAmount = BigDecimal.ZERO;
        }
        
        if (status.getAvailableBalance().compareTo(minAmount) < 0) {
            throw new IllegalArgumentException("Available balance is below minimum payout amount of " + minAmount);
        }
        
        log.info("PAYOUT_DEBUG: requestPayout delegating to executePayout for creatorId={}, amount={}", creatorId, status.getAvailableBalance());
        return payoutService.executePayout(creatorId, status.getAvailableBalance(), "EUR");
    }

    private Instant calculateNextPayoutDate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        ZonedDateTime nextPayout = now.withHour(3).withMinute(0).withSecond(0).withNano(0);
        
        if (now.isAfter(nextPayout) || now.isEqual(nextPayout)) {
            nextPayout = nextPayout.plusDays(1);
        }
        
        return nextPayout.toInstant();
    }
}
