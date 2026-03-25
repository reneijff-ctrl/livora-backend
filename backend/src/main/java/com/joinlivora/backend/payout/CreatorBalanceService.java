package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.EarningsBreakdownDTO;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreatorBalanceService {

    private final CreatorEarningRepository earningRepository;

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getAvailableBalance(User creator) {
        List<CreatorEarning> earnings = earningRepository.findAllByCreatorOrderByCreatedAtDesc(creator);
        
        return earnings.stream()
                .filter(e -> !e.isLocked())
                .filter(this::isHoldReleasedOrNone)
                .collect(Collectors.groupingBy(
                        CreatorEarning::getCurrency,
                        Collectors.reducing(BigDecimal.ZERO, CreatorEarning::getNetAmount, BigDecimal::add)
                ));
    }

    @Transactional(readOnly = true)
    public EarningsBreakdownDTO getEarningsBreakdown(User creator) {
        List<CreatorEarning> earnings = earningRepository.findAllByCreatorOrderByCreatedAtDesc(creator);

        long totalCount = 0;
        BigDecimal totalSum = BigDecimal.ZERO;

        long availableCount = 0;
        BigDecimal availableSum = BigDecimal.ZERO;

        long lockedCount = 0;
        BigDecimal lockedSum = BigDecimal.ZERO;

        long payoutHoldCount = 0;
        BigDecimal payoutHoldSum = BigDecimal.ZERO;

        long fraudHoldCount = 0;
        BigDecimal fraudHoldSum = BigDecimal.ZERO;

        long payoutRequestedCount = 0;
        BigDecimal payoutRequestedSum = BigDecimal.ZERO;

        long manualAdminLockCount = 0;
        BigDecimal manualAdminLockSum = BigDecimal.ZERO;

        for (CreatorEarning e : earnings) {
            BigDecimal eurValue = convertToEur(e.getNetAmount(), e.getCurrency());
            
            totalCount++;
            totalSum = totalSum.add(eurValue);

            if (!e.isLocked() && isHoldReleasedOrNone(e)) {
                availableCount++;
                availableSum = availableSum.add(eurValue);
            } else {
                lockedCount++;
                lockedSum = lockedSum.add(eurValue);

                if (e.getPayoutHold() != null) {
                    payoutHoldCount++;
                    payoutHoldSum = payoutHoldSum.add(eurValue);
                } else if (e.getHoldPolicy() != null) {
                    fraudHoldCount++;
                    fraudHoldSum = fraudHoldSum.add(eurValue);
                } else if (e.getPayout() != null || e.getPayoutRequest() != null) {
                    payoutRequestedCount++;
                    payoutRequestedSum = payoutRequestedSum.add(eurValue);
                } else {
                    manualAdminLockCount++;
                    manualAdminLockSum = manualAdminLockSum.add(eurValue);
                }
            }
        }

        return EarningsBreakdownDTO.builder()
                .totalEarnings(new EarningsBreakdownDTO.SummaryDTO(totalCount, totalSum))
                .availableEarnings(new EarningsBreakdownDTO.SummaryDTO(availableCount, availableSum))
                .lockedEarnings(new EarningsBreakdownDTO.SummaryDTO(lockedCount, lockedSum))
                .lockedBy(EarningsBreakdownDTO.LockedByDTO.builder()
                        .payoutHold(new EarningsBreakdownDTO.SummaryDTO(payoutHoldCount, payoutHoldSum))
                        .fraudHold(new EarningsBreakdownDTO.SummaryDTO(fraudHoldCount, fraudHoldSum))
                        .payoutRequested(new EarningsBreakdownDTO.SummaryDTO(payoutRequestedCount, payoutRequestedSum))
                        .manualAdminLock(new EarningsBreakdownDTO.SummaryDTO(manualAdminLockCount, manualAdminLockSum))
                        .build())
                .build();
    }

    private BigDecimal convertToEur(BigDecimal amount, String currency) {
        if ("EUR".equalsIgnoreCase(currency)) {
            return amount;
        }
        if ("TOKEN".equalsIgnoreCase(currency)) {
            return amount.multiply(CreatorEarningsService.TOKEN_TO_EUR_RATE);
        }
        // Default to amount if currency unknown, or add more logic if needed
        return amount;
    }

    private boolean isHoldReleasedOrNone(CreatorEarning earning) {
        if (earning.getPayoutHold() == null) {
            return true;
        }
        return earning.getPayoutHold().getStatus() == PayoutHoldStatus.RELEASED;
    }
}
