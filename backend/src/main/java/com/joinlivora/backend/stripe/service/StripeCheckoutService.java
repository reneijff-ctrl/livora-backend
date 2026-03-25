package com.joinlivora.backend.stripe.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Balance;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.payout.StripeConnectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StripeCheckoutService {

    private final String successUrl;
    private final String cancelUrl;
    private final CreatorRepository creatorRepository;
    private final StripeConnectService stripeConnectService;

    @Value("${livora.monetization.platform-fee-percentage:30}")
    private int platformFeePercentage;

    @Value("${payments.stripe.enabled:true}")
    private boolean stripeEnabled;

    @Value("${stripe.currency:eur}")
    private String currency;

    public boolean isEnabled() {
        return stripeEnabled;
    }

    public boolean checkHealth() {
        if (!stripeEnabled) {
            return false;
        }
        try {
            Balance.retrieve();
            return true;
        } catch (StripeException e) {
            log.error("Stripe health check failed", e);
            return false;
        }
    }

    public StripeCheckoutService(
            @Value("${stripe.success-url}") String successUrl,
            @Value("${stripe.cancel-url}") String cancelUrl,
            CreatorRepository creatorRepository,
            StripeConnectService stripeConnectService
    ) {
        if (successUrl == null || !successUrl.startsWith("http")) {
            throw new IllegalArgumentException("Success URL must be an absolute URL starting with http/https");
        }
        if (cancelUrl == null || !cancelUrl.startsWith("http")) {
            throw new IllegalArgumentException("Cancel URL must be an absolute URL starting with http/https");
        }
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        this.creatorRepository = creatorRepository;
        this.stripeConnectService = stripeConnectService;
    }

    public String createCheckoutSession(
        Long creatorId,
        String creatorName,
        Long userId,
        Long amountCents
    ) {
        if (!stripeEnabled) {
            log.info("Stripe disabled: createCheckoutSession short-circuited");
            throw new IllegalStateException("Stripe disabled");
        }
        if (creatorId == null) {
            throw new IllegalArgumentException("creator is mandatory");
        }
        if (amountCents == null || amountCents < 100) {
            throw new IllegalArgumentException("Amount must be at least 100 cents");
        }

        String productName = "Tip";
        if (creatorName != null && !creatorName.isBlank()) {
            productName = "Tip for " + creatorName;
        }

        try {
            SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCurrency(currency)
                .setSuccessUrl(successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl + (cancelUrl.contains("?") ? "&" : "?") + "creator=" + creatorId)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency)
                                .setUnitAmount(amountCents)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(productName)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .putMetadata("creator", String.valueOf(creatorId))
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("type", "TIP");

            // DESTINATION CHARGES: Calculate fee and set transfer destination
            // All creator earnings are technically stored in the platform balance first,
            // but Stripe Connect "Destination Charges" allow automatic split at source.
            // TODO(livora-payments): In the future, we might want to use "Separate Charges and Transfers" 
            // to have more control over when the money is actually moved to the creator's account.
            creatorRepository.findById(creatorId).ifPresent(creator -> {
                stripeConnectService.getAccountByCreatorId(creator.getUser().getId()).ifPresent(account -> {
                    if (account.isChargesEnabled()) {
                        long applicationFeeAmount = (amountCents * platformFeePercentage) / 100;
                        
                        builder.setPaymentIntentData(
                            SessionCreateParams.PaymentIntentData.builder()
                                .setApplicationFeeAmount(applicationFeeAmount)
                                .setTransferData(
                                    SessionCreateParams.PaymentIntentData.TransferData.builder()
                                        .setDestination(account.getStripeAccountId())
                                        .build()
                                )
                                .build()
                        );
                        log.info("STRIPE: Using destination charges for creator {} with fee {} cents", creatorId, applicationFeeAmount);
                    } else {
                        log.info("STRIPE: Creator {} Stripe account not ready for charges, falling back to platform-only charge", creatorId);
                    }
                });
            });

            Session session = Session.create(builder.build());
            return session.getUrl();
        } catch (Exception e) {
            log.error("Stripe checkout session failed", e);
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
        }
    }
}
