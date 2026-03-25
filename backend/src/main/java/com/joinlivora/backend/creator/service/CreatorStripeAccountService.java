package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.dto.CreatorStripeStatusResponse;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.CreatorStripeAccount;
import com.joinlivora.backend.creator.model.StripeOnboardingStatus;
import com.joinlivora.backend.creator.repository.CreatorStripeAccountRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorStripeAccountService {

    private final CreatorStripeAccountRepository repository;
    private final StripeClient stripeClient;

    @Transactional(readOnly = true)
    public Optional<CreatorStripeAccount> getStripeAccount(CreatorProfile creator) {
        return repository.findByCreator(creator);
    }

    @Transactional(readOnly = true)
    public Optional<CreatorStripeAccount> getStripeAccountByCreatorId(Long creatorId) {
        return repository.findByCreatorId(creatorId);
    }

    @Transactional(readOnly = true)
    public Optional<CreatorStripeAccount> getStripeAccountByStripeId(String stripeAccountId) {
        return repository.findByStripeAccountId(stripeAccountId);
    }

    @Transactional
    public CreatorStripeAccount saveStripeAccount(CreatorStripeAccount account) {
        return repository.save(account);
    }

    @Transactional
    public String createOrGetStripeAccount(CreatorProfile creator) throws StripeException {
        return repository.findByCreator(creator)
                .map(CreatorStripeAccount::getStripeAccountId)
                .orElseGet(() -> {
                    try {
                        log.info("STRIPE: Creating Express account for creator: {}", creator.getUser().getEmail());
                        AccountCreateParams params = AccountCreateParams.builder()
                                .setType(AccountCreateParams.Type.EXPRESS)
                                .setEmail(creator.getUser().getEmail())
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

                        CreatorStripeAccount stripeAccount = CreatorStripeAccount.builder()
                                .creator(creator)
                                .stripeAccountId(accountId)
                                .onboardingCompleted(false)
                                .build();

                        repository.save(stripeAccount);
                        log.info("STRIPE: Created Express account {} for creator {}", accountId, creator.getUser().getEmail());
                        return accountId;
                    } catch (StripeException e) {
                        log.error("STRIPE: Failed to create Express account for creator {}", creator.getUser().getEmail(), e);
                        throw new RuntimeException("Failed to create Stripe account", e);
                    }
                });
    }

    public String generateOnboardingLink(String stripeAccountId, String returnUrl, String refreshUrl) throws StripeException {
        AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                .setAccount(stripeAccountId)
                .setRefreshUrl(refreshUrl)
                .setReturnUrl(returnUrl)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();

        AccountLink accountLink = stripeClient.accountLinks().create(params);
        return accountLink.getUrl();
    }

    @Transactional
    public CreatorStripeAccount createOrUpdateStripeAccount(CreatorProfile creator, String stripeAccountId, boolean onboardingCompleted) {
        CreatorStripeAccount account = repository.findByCreator(creator)
                .orElse(CreatorStripeAccount.builder()
                        .creator(creator)
                        .build());

        account.setStripeAccountId(stripeAccountId);
        account.setOnboardingCompleted(onboardingCompleted);

        return repository.save(account);
    }

    @Transactional
    public void updateAccountStatus(String stripeAccountId, boolean onboardingCompleted) {
        repository.findByStripeAccountId(stripeAccountId).ifPresent(account -> {
            account.setOnboardingCompleted(onboardingCompleted);
            repository.save(account);
            log.info("STRIPE: Updated account {} onboarding status: {}", stripeAccountId, onboardingCompleted);
        });
    }

    @Transactional
    public StripeOnboardingStatus refreshOnboardingStatus(CreatorProfile creator) throws StripeException {
        Optional<CreatorStripeAccount> accountOpt = repository.findByCreator(creator);
        if (accountOpt.isEmpty()) {
            return StripeOnboardingStatus.NOT_STARTED;
        }

        CreatorStripeAccount account = accountOpt.get();
        if (account.isOnboardingCompleted()) {
            return StripeOnboardingStatus.VERIFIED;
        }

        // Fetch latest from Stripe
        Account stripeAccount = stripeClient.accounts().retrieve(account.getStripeAccountId());
        boolean completed = stripeAccount.getDetailsSubmitted();

        if (completed) {
            account.setOnboardingCompleted(true);
            repository.save(account);
            return StripeOnboardingStatus.VERIFIED;
        }

        return StripeOnboardingStatus.PENDING;
    }

    @Transactional
    public CreatorStripeStatusResponse getStripeStatus(CreatorProfile creator) throws StripeException {
        Optional<CreatorStripeAccount> accountOpt = repository.findByCreator(creator);
        if (accountOpt.isEmpty()) {
            return CreatorStripeStatusResponse.builder()
                    .hasAccount(false)
                    .onboardingCompleted(false)
                    .payoutsEnabled(false)
                    .build();
        }

        CreatorStripeAccount account = accountOpt.get();
        Account stripeAccount = stripeClient.accounts().retrieve(account.getStripeAccountId());

        boolean onboardingCompleted = stripeAccount.getDetailsSubmitted();
        boolean payoutsEnabled = stripeAccount.getPayoutsEnabled();

        if (onboardingCompleted != account.isOnboardingCompleted()) {
            account.setOnboardingCompleted(onboardingCompleted);
            repository.save(account);
        }

        return CreatorStripeStatusResponse.builder()
                .hasAccount(true)
                .onboardingCompleted(onboardingCompleted)
                .payoutsEnabled(payoutsEnabled)
                .build();
    }
}
