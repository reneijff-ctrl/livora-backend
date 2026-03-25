package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.chat.ChatModerationService;
import com.joinlivora.backend.fraud.config.VelocityRulesConfig;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.VelocityActionType;
import com.joinlivora.backend.fraud.model.VelocityMetric;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.RealtimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VelocityAnomalyDetectorTest {

    @Mock
    private VelocityRulesConfig rulesConfig;
    @Mock
    private RuleFraudSignalRepository fraudSignalRepository;
    @Mock
    private UserRiskStateRepository userRiskStateRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatModerationService chatModerationService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private VelocityAnomalyDetector detector;

    private Long userId = 1L;
    private Instant now = Instant.now();

    @BeforeEach
    void setUp() {
    }

    @Test
    void evaluateMetric_SpamAction_ShouldMuteAndDisconnect() {
        VelocityRulesConfig.Rule rule = new VelocityRulesConfig.Rule();
        rule.setLimit(30);
        rule.setAction("spam");

        Map<String, VelocityRulesConfig.Rule> messageRules = new HashMap<>();
        messageRules.put("1m", rule);

        when(rulesConfig.getMessage()).thenReturn(messageRules);

        User user = new User();
        user.setId(userId);
        user.setEmail("spammer@test.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        VelocityMetric metric = VelocityMetric.builder()
                .userId(userId)
                .actionType(VelocityActionType.MESSAGE)
                .count(31)
                .windowStart(now)
                .windowEnd(now.plus(1, ChronoUnit.MINUTES))
                .build();

        User admin = new User();
        admin.setId(999L);
        admin.setRole(com.joinlivora.backend.user.Role.ADMIN);
        when(userRepository.findAll()).thenReturn(java.util.List.of(admin));

        detector.evaluateMetric(metric);

        // Verify RuleFraudSignal
        verify(fraudSignalRepository).save(argThat(signal ->
                signal.getUserId().equals(userId) &&
                signal.getRiskLevel() == FraudDecisionLevel.MEDIUM &&
                signal.getType() == com.joinlivora.backend.fraud.model.FraudSignalType.CHAT_SPAM &&
                "CHAT_SPAM".equals(signal.getReason())
        ));

        // Verify Mute (10 minutes)
        verify(chatModerationService).muteUser(eq(userId), eq(999L), eq(Duration.ofMinutes(10)), isNull());

        // Verify Disconnect message
        verify(messagingTemplate).convertAndSendToUser(
                eq("spammer@test.com"),
                eq("/queue/notifications"),
                argThat(msg -> {
                    RealtimeMessage rt = (RealtimeMessage) msg;
                    return "DISCONNECT".equals(rt.getType()) && rt.getPayload().get("type").toString().contains("spamming");
                })
        );
    }

    @Test
    void evaluateMetric_SuspiciousAction_ShouldSaveSignal() {
        VelocityRulesConfig.Rule rule = new VelocityRulesConfig.Rule();
        rule.setLimit(5);
        rule.setAction("suspicious");

        Map<String, VelocityRulesConfig.Rule> loginRules = new HashMap<>();
        loginRules.put("1m", rule);

        when(rulesConfig.getLogin()).thenReturn(loginRules);

        VelocityMetric metric = VelocityMetric.builder()
                .userId(userId)
                .actionType(VelocityActionType.LOGIN)
                .count(6)
                .windowStart(now)
                .windowEnd(now.plus(1, ChronoUnit.MINUTES))
                .build();

        detector.evaluateMetric(metric);

        verify(fraudSignalRepository).save(argThat(signal ->
                signal.getUserId().equals(userId) &&
                signal.getRiskLevel() == FraudDecisionLevel.MEDIUM &&
                signal.getType() == com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING &&
                signal.getReason().contains("Velocity Anomaly: LOGIN")
        ));
        verifyNoInteractions(userRiskStateRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void evaluateMetric_CriticalAction_ShouldEscalateUser() {
        VelocityRulesConfig.Rule rule = new VelocityRulesConfig.Rule();
        rule.setLimit(3);
        rule.setAction("critical");

        Map<String, VelocityRulesConfig.Rule> paymentRules = new HashMap<>();
        paymentRules.put("5m", rule);

        when(rulesConfig.getPayment()).thenReturn(paymentRules);

        User user = new User();
        user.setId(userId);
        user.setEmail("test@test.com");
        user.setFraudRiskLevel(FraudRiskLevel.LOW);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRiskStateRepository.findById(userId)).thenReturn(Optional.empty());

        VelocityMetric metric = VelocityMetric.builder()
                .userId(userId)
                .actionType(VelocityActionType.PAYMENT)
                .count(4)
                .windowStart(now)
                .windowEnd(now.plus(5, ChronoUnit.MINUTES))
                .build();

        detector.evaluateMetric(metric);

        verify(fraudSignalRepository).save(argThat(signal ->
                signal.getUserId().equals(userId) &&
                signal.getRiskLevel() == FraudDecisionLevel.HIGH
        ));
        verify(userRepository).save(argThat(u -> u.getFraudRiskLevel() == FraudRiskLevel.HIGH));
        verify(userRiskStateRepository).save(argThat(state ->
                state.getUserId().equals(userId) &&
                state.getCurrentRisk() == FraudDecisionLevel.HIGH &&
                state.isPaymentLocked() &&
                state.getBlockedUntil() != null
        ));
    }

    @Test
    void evaluateMetric_BelowLimit_ShouldDoNothing() {
        VelocityRulesConfig.Rule rule = new VelocityRulesConfig.Rule();
        rule.setLimit(10);
        rule.setAction("suspicious");

        Map<String, VelocityRulesConfig.Rule> tipRules = new HashMap<>();
        tipRules.put("1m", rule);

        when(rulesConfig.getTip()).thenReturn(tipRules);

        VelocityMetric metric = VelocityMetric.builder()
                .userId(userId)
                .actionType(VelocityActionType.TIP)
                .count(5)
                .windowStart(now)
                .windowEnd(now.plus(1, ChronoUnit.MINUTES))
                .build();

        detector.evaluateMetric(metric);

        verifyNoInteractions(fraudSignalRepository);
        verifyNoInteractions(userRiskStateRepository);
        verifyNoInteractions(userRepository);
    }
}









