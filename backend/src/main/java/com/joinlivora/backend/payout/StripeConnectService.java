package com.joinlivora.backend.payout;

import com.joinlivora.backend.creator.model.StripeOnboardingStatus;
import com.joinlivora.backend.user.User;
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

import java.util.Optional;

@Service("stripeConnectService")
@RequiredArgsConstructor
@Slf4j
public class StripeConnectService {

    private final StripeClient stripeClient;
    private final LegacyCreatorStripeAccountRepository repository;

    @Value("${payments.stripe.enabled:true}")
    private boolean stripeEnabled;

    /**
     * Creates or retrieves a Stripe Express account for a creator.
     */
    @Transactional
    public String createOrGetStripeAccount(User creator) {
        if (!stripeEnabled) {
            log.info("Stripe disabled: createOrGetStripeAccount short-circuited for {}", creator.getEmail());
            return repository.findByCreatorId(creator.getId())
                    .map(LegacyCreatorStripeAccount::getStripeAccountId)
                    .orElse("");
        }
        return repository.findByCreatorId(creator.getId())
                .map(LegacyCreatorStripeAccount::getStripeAccountId)
                .orElseGet(() -> {
                    try {
                        return createExpressAccount(creator);
                    } catch (StripeException e) {
                        log.error("STRIPE: Failed to create Express account for creator {}", creator.getEmail(), e);
                        throw new RuntimeException("Failed to create Stripe account", e);
                    }
                });
    }

    private String createExpressAccount(User creator) throws StripeException {
        log.info("STRIPE: Creating Express account for creator: {}", creator.getEmail());
        AccountCreateParams params = AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.EXPRESS)
                .setEmail(creator.getEmail())
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

        LegacyCreatorStripeAccount creatorStripeAccount = LegacyCreatorStripeAccount.builder()
                .creatorId(creator.getId())
                .stripeAccountId(accountId)
                .onboardingStatus(StripeOnboardingStatus.PENDING)
                .onboardingCompleted(false)
                .chargesEnabled(false)
                .payoutsEnabled(false)
                .build();

        repository.save(creatorStripeAccount);
        log.info("STRIPE: Created Express account {} for creator {}", accountId, creator.getEmail());
        return accountId;
    }

    public Optional<LegacyCreatorStripeAccount> getAccountByCreatorId(Long creatorId) {
        return repository.findByCreatorId(creatorId);
    }

    public Optional<LegacyCreatorStripeAccount> getAccountByStripeId(String stripeAccountId) {
        log.info("PAYOUT_DEBUG: getAccountByStripeId lookup for stripeAccountId={}", stripeAccountId);
        return repository.findByStripeAccountId(stripeAccountId);
    }

    /**
     * Generates an onboarding link for a Stripe Express account.
     */
    public String generateOnboardingLink(String stripeAccountId, String returnUrl, String refreshUrl) throws StripeException {
        if (!stripeEnabled) {
            log.info("Stripe disabled: generateOnboardingLink short-circuited for account {}", stripeAccountId);
            return "";
        }
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
     * Updates the status of a creator's Stripe account based on webhook data.
     */
    @Transactional
    public void updateAccountStatus(String stripeAccountId, boolean chargesEnabled, boolean payoutsEnabled, boolean onboardingCompleted) {
        repository.findByStripeAccountId(stripeAccountId).ifPresent(account -> {
            boolean previousCharges = account.isChargesEnabled();
            boolean previousPayouts = account.isPayoutsEnabled();
            boolean previousOnboarding = account.isOnboardingCompleted();

            account.setChargesEnabled(chargesEnabled);
            account.setPayoutsEnabled(payoutsEnabled);
            account.setOnboardingCompleted(onboardingCompleted);
            
            if (onboardingCompleted) {
                account.setOnboardingStatus(StripeOnboardingStatus.VERIFIED);
            } else {
                account.setOnboardingStatus(StripeOnboardingStatus.PENDING);
            }

            repository.save(account);

            log.info("STRIPE: Updated account {} status - Charges: {} -> {}, Payouts: {} -> {}, Onboarding: {} -> {}, Status: {}",
                    stripeAccountId, previousCharges, chargesEnabled, previousPayouts, payoutsEnabled, previousOnboarding, onboardingCompleted, account.getOnboardingStatus());
        });
    }

    @Transactional
    public StripeOnboardingStatus refreshOnboardingStatus(User user) throws StripeException {
        Optional<LegacyCreatorStripeAccount> accountOpt = repository.findByCreatorId(user.getId());
        if (accountOpt.isEmpty()) {
            return StripeOnboardingStatus.NOT_STARTED;
        }

        LegacyCreatorStripeAccount account = accountOpt.get();
        if (!stripeEnabled) {
            return account.isOnboardingCompleted() || account.getOnboardingStatus() == StripeOnboardingStatus.VERIFIED
                    ? StripeOnboardingStatus.VERIFIED
                    : StripeOnboardingStatus.PENDING;
        }

        if (account.getOnboardingStatus() == StripeOnboardingStatus.VERIFIED) {
            return StripeOnboardingStatus.VERIFIED;
        }

        // Fetch latest from Stripe
        Account stripeAccount = stripeClient.accounts().retrieve(account.getStripeAccountId());
        boolean completed = stripeAccount.getDetailsSubmitted();
        
        if (completed) {
            account.setOnboardingCompleted(true);
            account.setOnboardingStatus(StripeOnboardingStatus.VERIFIED);
            repository.save(account);
            return StripeOnboardingStatus.VERIFIED;
        }

        return StripeOnboardingStatus.PENDING;
    }
}
