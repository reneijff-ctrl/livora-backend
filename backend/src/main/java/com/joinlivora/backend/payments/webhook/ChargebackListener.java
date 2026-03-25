package com.joinlivora.backend.payments.webhook;

import com.joinlivora.backend.chargeback.ChargebackService;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChargebackListener {

    private final ChargebackService chargebackService;

    @EventListener
    public void handleChargeback(ChargebackEvent event) {
        User user = event.getUser();
        String paymentIntentId = event.getPaymentIntentId();
        
        log.info("ChargebackListener: Handling chargeback event for creator {}", user.getEmail());
        chargebackService.handleDisputeCreated(user, paymentIntentId, event.getDispute());
    }
}
