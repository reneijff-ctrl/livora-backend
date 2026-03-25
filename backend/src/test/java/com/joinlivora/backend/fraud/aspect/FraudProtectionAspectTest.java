package com.joinlivora.backend.fraud.aspect;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.monetization.TipRepository;
import com.joinlivora.backend.monetization.TipOrchestrationService;
import com.joinlivora.backend.monetization.TipValidationService;
import com.joinlivora.backend.payout.*;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.token.TipRecordRepository;
import com.joinlivora.backend.token.TokenWalletService;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.stripe.StripeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class FraudProtectionAspectTest {

    @Autowired
    private TipOrchestrationService tipService;

    @Autowired
    private PayoutService payoutService;

    @MockBean
    private UserService userService;

    @MockBean
    private TipRepository tipRepository;
    @MockBean
    private CreatorEarningsService creatorEarningsService;
    @MockBean
    private AnalyticsEventPublisher analyticsEventPublisher;
    @MockBean
    private SimpMessagingTemplate messagingTemplate;
    @MockBean
    private StripeClient stripeClient;
    @MockBean
    private TokenWalletService tokenWalletService;
    @MockBean
    private StreamRepository StreamRepository;
    @MockBean
    private TipRecordRepository tipRecordRepository;
    @MockBean
    private TipValidationService tipValidationService;

    @MockBean
    private PayoutRepository payoutRepository;
    @MockBean
    private CreatorPayoutRepository creatorPayoutRepository;
    @MockBean
    private CreatorPayoutSettingsRepository creatorPayoutSettingsRepository;
    @MockBean
    private StripeAccountRepository stripeAccountRepository;
    @MockBean
    private CreatorEarningRepository creatorEarningRepository;
    @MockBean
    private com.joinlivora.backend.token.CreatorEarningsRepository creatorEarningsRepository;

    @Test
    @WithMockUser(username = "highrisk@test.com")
    void shouldBlockTipWhenRiskIsHigh() {
        User user = new User();
        user.setEmail("highrisk@test.com");
        user.setFraudRiskLevel(FraudRiskLevel.HIGH);

        when(userService.getByEmail("highrisk@test.com")).thenReturn(user);

        assertThrows(AccessDeniedException.class, () -> {
            tipService.createTipIntent(user, 1L, new BigDecimal("10.00"), "message", "request-creator", "127.0.0.1", "US", "Test UA", "fp");
        });
    }

    @Test
    @WithMockUser(username = "highrisk@test.com")
    void shouldBlockPayoutWhenRiskIsHigh() {
        User user = new User();
        user.setEmail("highrisk@test.com");
        user.setFraudRiskLevel(FraudRiskLevel.HIGH);

        when(userService.getByEmail("highrisk@test.com")).thenReturn(user);

        assertThrows(AccessDeniedException.class, () -> {
            payoutService.executePayout(UUID.randomUUID(), new BigDecimal("100.00"), "EUR");
        });
    }

    @Test
    @WithMockUser(username = "lowrisk@test.com")
    void shouldAllowActionWhenRiskIsLow() {
        User user = new User();
        user.setEmail("lowrisk@test.com");
        user.setFraudRiskLevel(FraudRiskLevel.LOW);

        when(userService.getByEmail("lowrisk@test.com")).thenReturn(user);

        try {
            // It might still throw other exceptions due to mocks, but not AccessDeniedException from aspect
            tipService.createTipIntent(user, 1L, new BigDecimal("10.00"), "message", "request-creator", "127.0.0.1", "US", "Test UA", "fp");
        } catch (AccessDeniedException e) {
            if (e.getMessage().contains("high fraud risk level")) {
                org.junit.jupiter.api.Assertions.fail("Should not have been blocked by FraudProtectionAspect");
            }
        } catch (Exception e) {
            // Unrelated errors are fine
        }
    }
}










