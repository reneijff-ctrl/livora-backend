package com.joinlivora.backend.payments.webhook;

import com.joinlivora.backend.user.User;
import com.stripe.model.Dispute;
import org.springframework.context.ApplicationEvent;

public class ChargebackEvent extends ApplicationEvent {
    private final User user;
    private final String paymentIntentId;
    private final Dispute dispute;

    public ChargebackEvent(Object source, User user, String paymentIntentId, Dispute dispute) {
        super(source);
        this.user = user;
        this.paymentIntentId = paymentIntentId;
        this.dispute = dispute;
    }

    public User getUser() {
        return user;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public Dispute getDispute() {
        return dispute;
    }
}
