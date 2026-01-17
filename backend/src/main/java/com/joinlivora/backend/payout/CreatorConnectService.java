package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import com.stripe.StripeClient;
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
public class CreatorConnectService {

    private final StripeAccountRepository stripeAccountRepository;
    private final StripeClient stripeClient;

    @Value("${stripe.success-url}")
    private String successUrl; 

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public String createOnboardingLink(User user) throws Exception {
        StripeAccount stripeAccount = stripeAccountRepository.findByUser(user)
                .orElseGet(() -> {
                    try {
                        AccountCreateParams params = AccountCreateParams.builder()
                                .setType(AccountCreateParams.Type.EXPRESS)
                                .setEmail(user.getEmail())
                                .setCapabilities(
                                        AccountCreateParams.Capabilities.builder()
                                                .setTransfers(AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build())
                                                .build()
                                )
                                .build();
                        Account account = stripeClient.accounts().create(params);
                        
                        StripeAccount newAccount = StripeAccount.builder()
                                .user(user)
                                .stripeAccountId(account.getId())
                                .build();
                        return stripeAccountRepository.save(newAccount);
                    } catch (Exception e) {
                        log.error("Failed to create Stripe Connect account for user: {}", user.getEmail(), e);
                        throw new RuntimeException("Stripe account creation failed");
                    }
                });

        AccountLinkCreateParams linkParams = AccountLinkCreateParams.builder()
                .setAccount(stripeAccount.getStripeAccountId())
                .setRefreshUrl(frontendUrl + "/creator/onboarding/refresh")
                .setReturnUrl(frontendUrl + "/creator/onboarding/complete")
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();

        AccountLink accountLink = stripeClient.accountLinks().create(linkParams);
        return accountLink.getUrl();
    }

    @Transactional
    public void updateAccountStatus(String stripeAccountId, boolean chargesEnabled, boolean payoutsEnabled) {
        stripeAccountRepository.findByStripeAccountId(stripeAccountId).ifPresent(account -> {
            account.setChargesEnabled(chargesEnabled);
            account.setPayoutsEnabled(payoutsEnabled);
            if (chargesEnabled && payoutsEnabled) {
                account.setOnboardingCompleted(true);
            }
            stripeAccountRepository.save(account);
            log.info("SECURITY: Updated Stripe account {} status: chargesEnabled={}, payoutsEnabled={}", 
                    stripeAccountId, chargesEnabled, payoutsEnabled);
        });
    }
}
