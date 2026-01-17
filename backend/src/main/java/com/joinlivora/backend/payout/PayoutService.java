package com.joinlivora.backend.payout;

import com.joinlivora.backend.token.CreatorEarnings;
import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.user.User;
import com.stripe.StripeClient;
import com.stripe.model.Transfer;
import com.stripe.param.TransferCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final StripeAccountRepository stripeAccountRepository;
    private final TokenService tokenService;
    private final StripeClient stripeClient;

    private static final long MIN_PAYOUT_TOKENS = 5000; // e.g. 5000 tokens = 50 EUR
    private static final double TOKEN_TO_EUR_RATE = 0.01; // 1 token = 0.01 EUR

    @Transactional
    public Payout requestPayout(User user, long tokens) throws Exception {
        if (tokens < MIN_PAYOUT_TOKENS) {
            throw new RuntimeException("Minimum payout is " + MIN_PAYOUT_TOKENS + " tokens");
        }

        StripeAccount stripeAccount = stripeAccountRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Stripe account not found. Please complete onboarding."));

        if (!stripeAccount.isPayoutsEnabled()) {
            throw new RuntimeException("Payouts are not enabled for your Stripe account.");
        }

        CreatorEarnings earnings = tokenService.getCreatorEarnings(user);
        if (earnings.getAvailableTokens() < tokens) {
            throw new RuntimeException("Insufficient available tokens for payout");
        }

        // 1. Lock earnings
        earnings.setAvailableTokens(earnings.getAvailableTokens() - tokens);
        tokenService.updateCreatorEarnings(earnings);

        BigDecimal eurAmount = BigDecimal.valueOf(tokens).multiply(BigDecimal.valueOf(TOKEN_TO_EUR_RATE));

        // 2. Create Payout record
        Payout payout = Payout.builder()
                .user(user)
                .tokenAmount(tokens)
                .eurAmount(eurAmount)
                .status(PayoutStatus.PENDING)
                .build();
        payout = payoutRepository.save(payout);

        try {
            // 3. Create Stripe Transfer
            TransferCreateParams params = TransferCreateParams.builder()
                    .setAmount(eurAmount.multiply(BigDecimal.valueOf(100)).longValue()) // In cents
                    .setCurrency("eur")
                    .setDestination(stripeAccount.getStripeAccountId())
                    .putMetadata("payoutId", payout.getId().toString())
                    .putMetadata("creatorId", user.getId().toString())
                    .build();

            Transfer transfer = stripeClient.transfers().create(params);
            payout.setStripeTransferId(transfer.getId());
            payoutRepository.save(payout);
            
            log.info("SECURITY: Created Stripe transfer {} for payout {}", transfer.getId(), payout.getId());
            return payout;
        } catch (Exception e) {
            log.error("SECURITY: Stripe transfer failed for payout {}", payout.getId(), e);
            // In a real system, we might want to mark as FAILED and restore earnings immediately 
            // OR keep as PENDING for manual retry. Here we'll mark as FAILED.
            payout.setStatus(PayoutStatus.FAILED);
            payout.setErrorMessage(e.getMessage());
            payoutRepository.save(payout);
            
            earnings.setAvailableTokens(earnings.getAvailableTokens() + tokens);
            tokenService.updateCreatorEarnings(earnings);
            throw e;
        }
    }

    public List<Payout> getPayoutHistory(User user) {
        return payoutRepository.findAllByUserOrderByCreatedAtDesc(user);
    }
}
