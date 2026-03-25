package com.joinlivora.backend.monetization;

import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.token.TokenWalletService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.wallet.WalletTransactionType;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TipPaymentService {

    private final StripeClient stripeClient;
    private final TokenWalletService tokenWalletService;
    @Lazy private final CreatorEarningsService creatorEarningsService;

    public PaymentIntent createStripePaymentIntent(User fromUser, User creator, BigDecimal amount, String message, String clientRequestId, RiskLevel riskLevel, Map<String, String> extraMetadata) throws Exception {
        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amount.multiply(new BigDecimal("100")).longValue())
                .setCurrency("eur")
                .putMetadata("type", "tip")
                .putMetadata("from_user_id", fromUser.getId().toString())
                .putMetadata("creator_id", creator.getId().toString())
                .putMetadata("message", message != null ? message : "")
                .putMetadata("client_request_id", clientRequestId != null ? clientRequestId : "")
                .putMetadata("fraud_risk_level", riskLevel != null ? riskLevel.name() : "LOW")
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC);

        if (creator.getStripeAccountId() != null && !creator.getStripeAccountId().isBlank()) {
            BigDecimal feeRate = creatorEarningsService.getPlatformFeeRate();
            long applicationFeeAmount = amount
                    .multiply(feeRate)
                    .multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();

            builder.setApplicationFeeAmount(applicationFeeAmount)
                    .setTransferData(
                            PaymentIntentCreateParams.TransferData.builder()
                                    .setDestination(creator.getStripeAccountId())
                                    .build()
                    );
        }

        if (extraMetadata != null) {
            extraMetadata.forEach(builder::putMetadata);
        }

        return stripeClient.paymentIntents().create(builder.build());
    }

    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws Exception {
        return stripeClient.paymentIntents().retrieve(paymentIntentId);
    }

    public long getAvailableTokenBalance(Long userId) {
        return tokenWalletService.getAvailableBalance(userId);
    }

    @Transactional
    public void deductTokens(Long userId, long amount, UUID roomId) {
        tokenWalletService.deductTokens(userId, amount, WalletTransactionType.TIP, "Tip to room " + roomId);
    }

    @Transactional
    public void recordTokenTipEarning(User viewer, User creator, long amount, UUID roomId, RiskLevel riskLevel) {
        creatorEarningsService.recordTokenTipEarning(viewer, creator, amount, roomId, riskLevel);
    }

    public BigDecimal getPlatformFeeRate() {
        return creatorEarningsService.getPlatformFeeRate();
    }
}
