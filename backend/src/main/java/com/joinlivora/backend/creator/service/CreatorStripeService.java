package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.dto.CreatorStripeStatusResponse;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorStripeService {

    private final UserRepository userRepository;
    private final StripeClient stripeClient;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${payments.stripe.enabled:true}")
    private boolean stripeEnabled;

    /**
     * Creates a Stripe Express account for the creator if one doesn't exist,
     * otherwise returns the existing account ID.
     */
    @Transactional
    public String createOrGetStripeAccount(User user) throws StripeException {
        if (!stripeEnabled) {
            log.warn("STRIPE: Stripe is disabled. Skipping account creation for {}", user.getEmail());
            return user.getStripeAccountId() != null ? user.getStripeAccountId() : "";
        }

        if (user.getStripeAccountId() != null && !user.getStripeAccountId().isEmpty()) {
            log.info("STRIPE: Using existing Stripe account {} for user {}", user.getStripeAccountId(), user.getEmail());
            return user.getStripeAccountId();
        }

        log.info("STRIPE: Creating new Express account for creator: {}", user.getEmail());
        AccountCreateParams params = AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.EXPRESS)
                .setEmail(user.getEmail())
                .setCapabilities(
                        AccountCreateParams.Capabilities.builder()
                                .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                                        .setRequested(true)
                                        .build())
                                .build()
                )
                .build();

        Account account = stripeClient.accounts().create(params);
        String accountId = account.getId();

        user.setStripeAccountId(accountId);
        userRepository.save(user);

        log.info("STRIPE: Successfully created Express account {} for user {}", accountId, user.getEmail());
        return accountId;
    }

    /**
     * Generates a Stripe onboarding link for the given account ID.
     */
    public String generateOnboardingLink(String stripeAccountId) throws StripeException {
        if (!stripeEnabled || stripeAccountId == null || stripeAccountId.isEmpty()) {
            return "";
        }

        log.info("STRIPE: Generating onboarding link for account: {}", stripeAccountId);
        
        String returnUrl = frontendUrl + "/creator/dashboard?stripe_onboarding=success";
        String refreshUrl = frontendUrl + "/creator/dashboard?stripe_onboarding=retry";

        AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                .setAccount(stripeAccountId)
                .setRefreshUrl(refreshUrl)
                .setReturnUrl(returnUrl)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();

        AccountLink accountLink = stripeClient.accountLinks().create(params);
        return accountLink.getUrl();
    }

    /**
     * Gets the latest Stripe account status for the user and syncs with database.
     */
    @Transactional
    public CreatorStripeStatusResponse getStripeStatus(User user) throws StripeException {
        if (!stripeEnabled || user.getStripeAccountId() == null || user.getStripeAccountId().isEmpty()) {
            return CreatorStripeStatusResponse.builder()
                    .hasAccount(false)
                    .onboardingCompleted(false)
                    .payoutsEnabled(false)
                    .build();
        }

        Account stripeAccount = stripeClient.accounts().retrieve(user.getStripeAccountId());

        boolean onboardingCompleted = stripeAccount.getDetailsSubmitted();
        boolean payoutsEnabled = stripeAccount.getPayoutsEnabled();

        // Sync with user entity if needed
        if (onboardingCompleted != user.isStripeOnboardingComplete() || payoutsEnabled != user.isPayoutsEnabled()) {
            user.setStripeOnboardingComplete(onboardingCompleted);
            user.setPayoutsEnabled(payoutsEnabled);
            userRepository.save(user);
        }

        return CreatorStripeStatusResponse.builder()
                .hasAccount(true)
                .onboardingCompleted(onboardingCompleted)
                .payoutsEnabled(payoutsEnabled)
                .build();
    }

    /**
     * Updates the status of a user's Stripe account based on webhook data.
     */
    @Transactional
    public void updateAccountStatus(String stripeAccountId, boolean onboardingCompleted, boolean payoutsEnabled) {
        userRepository.findByStripeAccountId(stripeAccountId).ifPresent(user -> {
            user.setStripeOnboardingComplete(onboardingCompleted);
            user.setPayoutsEnabled(payoutsEnabled);
            userRepository.save(user);
            log.info("STRIPE: Updated user {} status via webhook - Onboarding: {}, Payouts: {}",
                    user.getEmail(), onboardingCompleted, payoutsEnabled);
        });
    }
}
