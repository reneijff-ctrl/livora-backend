package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.payout.MonetizationService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TipService {

    private final TipRepository tipRepository;
    private final UserService userService;
    private final MonetizationService monetizationService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final StripeClient stripeClient;

    @Transactional
    public String createTipIntent(User fromUser, Long creatorId, BigDecimal amount, String message) throws Exception {
        User creator = userService.getById(creatorId);
        
        if (amount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Minimum tip amount is 1.00");
        }

        // 1. Create Stripe PaymentIntent
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.multiply(new BigDecimal("100")).longValue())
                .setCurrency("eur")
                .putMetadata("type", "tip")
                .putMetadata("from_user_id", fromUser.getId().toString())
                .putMetadata("creator_id", creator.getId().toString())
                .putMetadata("message", message != null ? message : "")
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                .build();

        PaymentIntent intent = stripeClient.paymentIntents().create(params);

        // 2. Persist Tip as PENDING
        Tip tip = Tip.builder()
                .fromUser(fromUser)
                .creator(creator)
                .amount(amount)
                .currency("eur")
                .message(message)
                .stripePaymentIntentId(intent.getId())
                .status(TipStatus.PENDING)
                .build();
        
        tipRepository.save(tip);

        log.info("MONETIZATION: Created Tip PaymentIntent {} for user {} to creator {}", 
                intent.getId(), fromUser.getEmail(), creator.getEmail());

        return intent.getClientSecret();
    }

    @Transactional
    public void confirmTip(String paymentIntentId) {
        tipRepository.findByStripePaymentIntentId(paymentIntentId).ifPresent(tip -> {
            if (tip.getStatus() == TipStatus.PAID) return;

            tip.setStatus(TipStatus.PAID);
            tipRepository.save(tip);

            // We will record earnings in StripeWebhookController via monetizationService
            // This method is called from webhook
            
            analyticsEventPublisher.publishEvent(
                    AnalyticsEventType.PAYMENT_SUCCEEDED,
                    tip.getFromUser(),
                    Map.of(
                            "type", "tip",
                            "amount", tip.getAmount(),
                            "creatorId", tip.getCreator().getId()
                    )
            );

            log.info("MONETIZATION: Tip confirmed and marked as PAID: {}", tip.getId());
            
            // Notify creator via WebSocket
            messagingTemplate.convertAndSendToUser(
                    tip.getCreator().getEmail(),
                    "/queue/notifications",
                    Map.of(
                            "type", "NEW_TIP",
                            "payload", Map.of(
                                    "amount", tip.getAmount(),
                                    "currency", tip.getCurrency(),
                                    "fromUser", tip.getFromUser().getEmail(),
                                    "message", tip.getMessage() != null ? tip.getMessage() : ""
                            )
                    )
            );
            
            // Also notify creator dashboard
            messagingTemplate.convertAndSendToUser(
                    tip.getCreator().getEmail(),
                    "/queue/creator/stats",
                    Map.of("type", "STATS_UPDATE")
            );
        });
    }
}
