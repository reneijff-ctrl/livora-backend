package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.SubscriptionResponse;
import com.joinlivora.backend.user.User;
import com.stripe.StripeClient;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final UserSubscriptionRepository subscriptionRepository;
    private final StripeClient stripeClient;

    @Cacheable(value = "subscriptions", key = "#user.email")
    public SubscriptionResponse getSubscriptionForUser(User user) {
        return subscriptionRepository.findFirstByUserOrderByCreatedAtDesc(user)
                .map(sub -> SubscriptionResponse.builder()
                        .status(sub.getStatus())
                        .currentPeriodEnd(sub.getCurrentPeriodEnd())
                        .cancelAtPeriodEnd(sub.isCancelAtPeriodEnd())
                        .nextInvoiceDate(sub.getNextInvoiceDate())
                        .paymentMethodBrand(sub.getPaymentMethodBrand())
                        .last4(sub.getLast4())
                        .build())
                .orElse(null);
    }

    @CacheEvict(value = "subscriptions", key = "#user.email")
    public void cancelSubscription(User user) throws Exception {
        UserSubscription userSub = subscriptionRepository.findFirstByUserOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new RuntimeException("No active subscription found"));

        if (userSub.getStripeSubscriptionId() != null) {
            log.info("SECURITY: Canceling Stripe subscription: {} for user: {}", userSub.getStripeSubscriptionId(), user.getEmail());
            Subscription stripeSub = stripeClient.subscriptions().retrieve(userSub.getStripeSubscriptionId());
            // cancel at end of period
            stripeSub.cancel(com.stripe.param.SubscriptionCancelParams.builder()
                    .setProrate(false)
                    .build());
            
            userSub.setStatus(SubscriptionStatus.CANCELED);
            subscriptionRepository.save(userSub);
        }
    }

    @CacheEvict(value = "subscriptions", key = "#user.email")
    public void resumeSubscription(User user) throws Exception {
        UserSubscription userSub = subscriptionRepository.findFirstByUserOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new RuntimeException("No subscription found"));

        if (userSub.getStatus() == SubscriptionStatus.CANCELED && userSub.getStripeSubscriptionId() != null) {
            log.info("SECURITY: Resuming Stripe subscription: {} for user: {}", userSub.getStripeSubscriptionId(), user.getEmail());
            Subscription stripeSub = stripeClient.subscriptions().retrieve(userSub.getStripeSubscriptionId());
            
            stripeSub.update(com.stripe.param.SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(false)
                    .build());
            
            userSub.setStatus(SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(userSub);
        }
    }

    public String createBillingPortalSession(User user) throws com.stripe.exception.StripeException {
        // Find latest subscription to get customer ID (or we can use email if customer exists)
        // In a real app, we should store Stripe Customer ID in User entity
        // For now, we'll try to find it via the subscription
        UserSubscription userSub = subscriptionRepository.findFirstByUserOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new RuntimeException("No subscription found"));
        
        if (userSub.getStripeSubscriptionId() == null) {
            throw new RuntimeException("No Stripe subscription ID found");
        }
        
        Subscription stripeSub = stripeClient.subscriptions().retrieve(userSub.getStripeSubscriptionId());
        String customerId = stripeSub.getCustomer();
        
        com.stripe.param.billingportal.SessionCreateParams params = 
                com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl("https://joinlivora.com/billing") // Should be configurable
                .build();
        
        com.stripe.model.billingportal.Session session = stripeClient.billingPortal().sessions().create(params);
        return session.getUrl();
    }

    @CacheEvict(value = "subscriptions", key = "#user.email")
    public void evictSubscriptionCache(User user) {
        // Method only for cache eviction
    }
}
