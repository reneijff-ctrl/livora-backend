package com.joinlivora.backend.monetization;

import com.joinlivora.backend.payout.CreatorEarningRepository;
import com.joinlivora.backend.payout.CreatorEarnings;
import com.joinlivora.backend.payout.LegacyCreatorProfile;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
import com.joinlivora.backend.payout.PayoutCreatorEarningsRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.payment.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TippingWebhookFlowIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TipRepository tipRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CreatorEarningRepository creatorEarningRepository;

    @Autowired
    private PayoutCreatorEarningsRepository payoutCreatorEarningsRepository;

    @Autowired
    private LegacyCreatorProfileRepository creatorProfileRepository;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("paymentsStripeWebhookService")
    private com.joinlivora.backend.payments.webhook.StripeWebhookService stripeWebhookService;

    private User viewer;
    private User creator;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("TRUNCATE TABLE analytics_events");
        jdbcTemplate.execute("TRUNCATE TABLE tips");
        jdbcTemplate.execute("TRUNCATE TABLE payments");
        jdbcTemplate.execute("TRUNCATE TABLE creator_earnings_history");
        jdbcTemplate.execute("TRUNCATE TABLE creator_earnings_balances");
        jdbcTemplate.execute("TRUNCATE TABLE creator_earnings");
        jdbcTemplate.execute("TRUNCATE TABLE creator_stats");
        jdbcTemplate.execute("TRUNCATE TABLE reputation_events");
        jdbcTemplate.execute("TRUNCATE TABLE reputation_change_logs");
        jdbcTemplate.execute("TRUNCATE TABLE creator_reputation_snapshot");
        jdbcTemplate.execute("TRUNCATE TABLE aml_risk_scores");
        jdbcTemplate.execute("TRUNCATE TABLE legacy_creator_profiles");
        jdbcTemplate.execute("TRUNCATE TABLE users");
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @BeforeEach
    void setUp() {
        viewer = new User();
        viewer.setEmail("viewer_webhook@test.com");
        viewer.setUsername("viewer_webhook");
        viewer.setPassword("password");
        viewer.setRole(Role.USER);
        viewer = userRepository.save(viewer);

        creator = new User();
        creator.setEmail("creator_webhook@test.com");
        creator.setUsername("creator_webhook");
        creator.setPassword("password");
        creator.setRole(Role.CREATOR);
        creator = userRepository.save(creator);

        LegacyCreatorProfile profile = LegacyCreatorProfile.builder()
                .user(creator)
                .username("creator_webhook")
                .displayName("Creator Webhook")
                .build();
        creatorProfileRepository.save(profile);
    }

    @Test
    void stripeWebhook_PaymentIntentSucceeded_ShouldCompleteTipAndRecordEarnings() throws Exception {
        // 1. Create a PENDING tip
        BigDecimal tipAmount = new BigDecimal("10.00");
        String paymentIntentId = "pi_test_webhook_" + System.currentTimeMillis();
        
        Tip tip = Tip.builder()
                .senderUserId(viewer)
                .creatorUserId(creator)
                .amount(tipAmount)
                .currency("eur")
                .stripePaymentIntentId(paymentIntentId)
                .status(TipStatus.PENDING)
                .build();
        tip = tipRepository.save(tip);

        // 2. Simulate Stripe PaymentIntent object
        String intentJson = "{" +
                "  \"id\": \"" + paymentIntentId + "\"," +
                "  \"object\": \"payment_intent\"," +
                "  \"amount\": 1000," +
                "  \"currency\": \"eur\"," +
                "  \"status\": \"succeeded\"," +
                "  \"metadata\": {" +
                "    \"type\": \"tip\"," +
                "    \"from_user_id\": \"" + viewer.getId() + "\"," +
                "    \"creator_id\": \"" + creator.getId() + "\"," +
                "    \"fraud_risk_level\": \"LOW\"" +
                "  }" +
                "}";
        
        com.stripe.model.PaymentIntent intent = com.stripe.net.ApiResource.GSON.fromJson(intentJson, com.stripe.model.PaymentIntent.class);

        // 3. Call service directly
        stripeWebhookService.handlePaymentIntentSucceeded(intent, "evt_test_id");

        // 4. Verify Tip status is COMPLETED
        Tip updatedTip = tipRepository.findById(tip.getId()).orElseThrow();
        assertThat(updatedTip.getStatus()).isEqualTo(TipStatus.COMPLETED);

        // 5. Verify Payment record exists
        assertThat(paymentRepository.existsByStripePaymentIntentId(paymentIntentId)).isTrue();

        // 6. Verify CreatorEarnings (Payout balance) updated with 30% fee applied
        // Amount = 10.00 EUR. Fee (30%) = 3.00 EUR. Net = 7.00 EUR.
        Optional<CreatorEarnings> earningsOpt = payoutCreatorEarningsRepository.findByCreator(creator);
        assertThat(earningsOpt).isPresent();
        CreatorEarnings earnings = earningsOpt.get();
        // Use compareTo for BigDecimal to ignore scale differences
        assertThat(earnings.getTotalEarned().compareTo(new BigDecimal("7.00"))).isEqualTo(0);
        assertThat(earnings.getAvailableBalance().compareTo(new BigDecimal("7.00"))).isEqualTo(0);

        // 7. Verify CreatorEarning (History) record
        assertThat(creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(creator)).hasSize(1);
    }
}








