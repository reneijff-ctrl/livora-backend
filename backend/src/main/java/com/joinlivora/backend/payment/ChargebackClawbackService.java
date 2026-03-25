package com.joinlivora.backend.payment;

import com.joinlivora.backend.payout.CreatorEarning;
import com.joinlivora.backend.payout.CreatorEarningRepository;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.payout.EarningSource;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.wallet.WalletTransaction;
import com.joinlivora.backend.wallet.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChargebackClawbackService {

    private final CreatorEarningRepository creatorEarningRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CreatorEarningsService creatorEarningsService;

    /**
     * Reverses creator earnings chronologically until the amount of tokens purchased by the payment is recovered.
     * This follows a FIFO (First-In, First-Out) attribution model.
     *
     * @param payment The payment that was chargebacked
     */
    @Transactional
    public void clawbackTokens(Payment payment) {
        if (payment == null) return;
        
        if (payment.getStripeSessionId() == null) {
            log.warn("Payment {} has no stripeSessionId, cannot determine token purchase for clawback", payment.getId());
            return;
        }

        // 1. Determine how many tokens were originally purchased by the payment
        String referenceId = "Stripe Session: " + payment.getStripeSessionId();
        Optional<WalletTransaction> purchaseTx = walletTransactionRepository.findByReferenceId(referenceId);
        
        if (purchaseTx.isEmpty()) {
            log.warn("No wallet transaction found for session {}, cannot determine tokens to clawback", payment.getStripeSessionId());
            return;
        }

        long tokensToClawback = purchaseTx.get().getAmount();
        User user = payment.getUser();
        
        if (tokensToClawback <= 0) {
            log.info("Wallet transaction for session {} shows non-positive amount ({}), skipping clawback", 
                    payment.getStripeSessionId(), tokensToClawback);
            return;
        }

        log.info("Starting FIFO clawback of {} tokens for user {}", tokensToClawback, user.getEmail());

        // 2. Retrieve creator earnings funded by the same user ordered by createdAt ascending
        List<CreatorEarning> earnings = creatorEarningRepository.findAllByUserAndCurrencyOrderByCreatedAtAsc(user, "TOKEN");

        long remainingToClawback = tokensToClawback;

        // 3. Reverse creator earnings chronologically until the chargeback amount is recovered
        for (CreatorEarning earning : earnings) {
            if (remainingToClawback <= 0) break;

            BigDecimal netAmount = earning.getNetAmount();
            // Skip already negative (reversals) or zero records
            if (netAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

            long netTokensAvailable = netAmount.longValue();
            long amountToReverse = Math.min(netTokensAvailable, remainingToClawback);

            if (amountToReverse > 0) {
                reverseEarning(earning, amountToReverse);
                remainingToClawback -= amountToReverse;
            }
        }

        if (remainingToClawback > 0) {
            log.warn("FIFO clawback for user {} incomplete: recovered {}/{} tokens from creators. Remaining: {}",
                    user.getEmail(), tokensToClawback - remainingToClawback, tokensToClawback, remainingToClawback);
        } else {
            log.info("FIFO clawback for user {} completed: all {} tokens recovered from creators", 
                    user.getEmail(), tokensToClawback);
        }
    }

    private void reverseEarning(CreatorEarning original, long tokensToReverse) {
        User creator = original.getCreator();
        BigDecimal amountToReverse = BigDecimal.valueOf(tokensToReverse);
        
        // Calculate reversal components based on the original earning's ratio
        BigDecimal originalNet = original.getNetAmount();
        BigDecimal ratio = amountToReverse.divide(originalNet, 10, RoundingMode.HALF_UP);
        
        BigDecimal reversalGross = original.getGrossAmount().multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal reversalFee = original.getPlatformFee().multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal reversalNet = amountToReverse; // The tokens we are taking back from the creator

        log.info("FIFO Clawback: Reversing {} tokens from creator {} (Original Earning: {})", 
                tokensToReverse, creator.getEmail(), original.getId());

        // 1. Adjust creator balances via CreatorEarningsService
        // Use pessimistic locking provided by service methods
        if (original.isLocked()) {
            creatorEarningsService.creditLockedBalance(creator, -tokensToReverse);
        } else {
            creatorEarningsService.creditCreatorBalance(creator, -tokensToReverse);
        }
        
        // Also adjust real-currency payout balance tracker
        BigDecimal netEurToDeduct = creatorEarningsService.convertToEur(reversalNet, "TOKEN");
        creatorEarningsService.creditPayoutEarnings(creator, netEurToDeduct.negate(), original.isLocked());

        // 2. Update PlatformBalance
        creatorEarningsService.updatePlatformBalances(reversalFee.negate(), reversalNet.negate(), "TOKEN");

        // 3. Update CreatorStats (Live Stats)
        creatorEarningsService.updateLiveStats(creator, BigDecimal.ZERO, -tokensToReverse, original.getSourceType());

        // 4. Create a negative CreatorEarning history record
        CreatorEarning reversal = CreatorEarning.builder()
                .creator(creator)
                .user(original.getUser())
                .grossAmount(reversalGross.negate())
                .platformFee(reversalFee.negate())
                .netAmount(reversalNet.negate())
                .currency("TOKEN")
                .sourceType(EarningSource.CHARGEBACK)
                .locked(original.isLocked())
                .dryRun(original.isDryRun())
                .build();
        
        creatorEarningRepository.save(reversal);
    }
}
