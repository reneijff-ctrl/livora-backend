package com.joinlivora.backend.payout;

import com.joinlivora.backend.payment.Payment;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonetizationService {

    private final CreatorEarningRepository creatorEarningRepository;
    private final PaymentRepository paymentRepository;
    
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.20"); // 20% platform commission

    @Transactional
    public void recordSubscriptionEarning(Payment payment, User creator) {
        processEarning(payment, creator, EarningSource.SUBSCRIPTION);
    }

    @Transactional
    public void recordTipEarning(Payment payment, User creator) {
        processEarning(payment, creator, EarningSource.TIP);
    }

    @Transactional
    public void recordPPVEarning(Payment payment, User creator) {
        processEarning(payment, creator, EarningSource.PPV);
    }

    private void processEarning(Payment payment, User creator, EarningSource source) {
        if (creator == null) {
            log.warn("No creator associated with payment {}, skipping creator earning record", payment.getId());
            return;
        }

        BigDecimal gross = payment.getAmount();
        BigDecimal fee = gross.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(fee);

        CreatorEarning earning = CreatorEarning.builder()
                .creator(creator)
                .amountGross(gross)
                .platformFee(fee)
                .amountNet(net)
                .currency(payment.getCurrency())
                .source(source)
                .stripeChargeId(payment.getStripePaymentIntentId())
                .build();

        creatorEarningRepository.save(earning);
        
        // Update payment with creator reference if not already set
        if (payment.getCreator() == null) {
            payment.setCreator(creator);
            paymentRepository.save(payment);
        }

        log.info("MONETIZATION: Recorded {} earning for creator {}: Gross={}, Fee={}, Net={}", 
                source, creator.getEmail(), gross, fee, net);
    }

    public Map<String, Object> getCreatorStats(User creator) {
        BigDecimal totalNet = creatorEarningRepository.sumNetEarningsByCreatorAndSince(creator, java.time.Instant.EPOCH);
        if (totalNet == null) totalNet = BigDecimal.ZERO;

        long subCount = creatorEarningRepository.countByCreatorAndSource(creator, EarningSource.SUBSCRIPTION);
        long tipCount = creatorEarningRepository.countByCreatorAndSource(creator, EarningSource.TIP);

        return Map.of(
            "totalNetEarnings", totalNet,
            "subscriptionCount", subCount,
            "tipsCount", tipCount
        );
    }
}
