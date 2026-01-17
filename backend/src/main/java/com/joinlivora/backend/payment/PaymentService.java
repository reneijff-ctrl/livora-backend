package com.joinlivora.backend.payment;

import com.joinlivora.backend.user.User;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final StripeClient stripeClient;

    @Value("${stripe.premium-plan-id}")
    private String premiumPlanId;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    public String createCheckoutSession(User user) throws StripeException {
        return createCheckoutSession(user, premiumPlanId, SessionCreateParams.Mode.SUBSCRIPTION);
    }

    public String createTokenCheckoutSession(User user, String stripePriceId, UUID packageId) throws StripeException {
        log.info("SECURITY: Creating Stripe token checkout session for user: {} package: {}", user.getEmail(), packageId);
        
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl + "?tokens=success&package=" + packageId)
                .setCancelUrl(cancelUrl)
                .setCustomerEmail(user.getEmail())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPrice(stripePriceId)
                                .build()
                )
                .setClientReferenceId(user.getId().toString())
                .putMetadata("package_id", packageId.toString())
                .putMetadata("type", "token_purchase")
                .build();

        Session session = stripeClient.checkout().sessions().create(params);
        return session.getUrl();
    }

    private String createCheckoutSession(User user, String priceId, SessionCreateParams.Mode mode) throws StripeException {
        log.info("SECURITY: Creating Stripe checkout session for user: {} mode: {}", user.getEmail(), mode);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(mode)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setCustomerEmail(user.getEmail())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPrice(priceId)
                                .build()
                )
                .setClientReferenceId(user.getId().toString())
                .build();

        Session session = stripeClient.checkout().sessions().create(params);
        return session.getUrl();
    }
}
