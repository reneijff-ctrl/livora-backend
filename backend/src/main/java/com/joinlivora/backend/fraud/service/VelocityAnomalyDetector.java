package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.chat.ChatModerationService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.fraud.config.VelocityRulesConfig;
import com.joinlivora.backend.fraud.model.*;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service("velocityAnomalyDetector")
@Slf4j
public class VelocityAnomalyDetector {

    private final VelocityRulesConfig rulesConfig;
    private final RuleFraudSignalRepository fraudSignalRepository;
    private final UserRiskStateRepository userRiskStateRepository;
    private final UserRepository userRepository;
    private final ChatModerationService chatModerationService;
    private final SimpMessagingTemplate messagingTemplate;

    public VelocityAnomalyDetector(
            VelocityRulesConfig rulesConfig,
            RuleFraudSignalRepository fraudSignalRepository,
            UserRiskStateRepository userRiskStateRepository,
            UserRepository userRepository,
            @Lazy ChatModerationService chatModerationService,
            @Lazy SimpMessagingTemplate messagingTemplate) {
        this.rulesConfig = rulesConfig;
        this.fraudSignalRepository = fraudSignalRepository;
        this.userRiskStateRepository = userRiskStateRepository;
        this.userRepository = userRepository;
        this.chatModerationService = chatModerationService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public void evaluateMetric(VelocityMetric metric) {
        log.debug("Evaluating velocity metric for creator {}: action={}, count={}, window=[{}, {}]",
                metric.getUserId(), metric.getActionType(), metric.getCount(), metric.getWindowStart(), metric.getWindowEnd());

        Map<String, VelocityRulesConfig.Rule> rules = getRulesForAction(metric.getActionType());
        if (rules == null) {
            log.debug("No rules found for action type: {}", metric.getActionType());
            return;
        }

        String windowKey = getWindowKey(metric.getWindowStart(), metric.getWindowEnd());
        VelocityRulesConfig.Rule rule = rules.get(windowKey);

        if (rule != null) {
            log.debug("Applying rule for action {}: window={}, limit={}, actual={}", 
                    metric.getActionType(), windowKey, rule.getLimit(), metric.getCount());
            if (metric.getCount() > rule.getLimit()) {
                handleAnomaly(metric.getUserId(), metric.getActionType(), windowKey, rule, metric.getCount());
            }
        } else {
            log.debug("No specific rule found for action {} in window {}", metric.getActionType(), windowKey);
        }
    }

    private Map<String, VelocityRulesConfig.Rule> getRulesForAction(VelocityActionType actionType) {
        return switch (actionType) {
            case LOGIN -> rulesConfig.getLogin();
            case TIP -> rulesConfig.getTip();
            case PAYMENT -> rulesConfig.getPayment();
            case MESSAGE -> rulesConfig.getMessage();
        };
    }

    private String getWindowKey(Instant start, Instant end) {
        long durationMinutes = ChronoUnit.MINUTES.between(start, end);
        if (durationMinutes == 1) return "1m";
        if (durationMinutes == 5) return "5m";
        if (durationMinutes == 10) return "10m";
        if (durationMinutes == 60) return "1h";
        return durationMinutes + "m";
    }

    private void handleAnomaly(Long userId, VelocityActionType actionType, String window, VelocityRulesConfig.Rule rule, int actualCount) {
        String baseReason = String.format("Velocity Anomaly: %s (%s window). Limit: %d, Actual: %d. Action: %s",
                actionType, window, rule.getLimit(), actualCount, rule.getAction());

        if ("spam".equalsIgnoreCase(rule.getAction())) {
            log.warn("SECURITY [velocity_anomaly]: Spam activity detected for creator {}: {}", userId, baseReason);
            saveSignal(userId, FraudDecisionLevel.MEDIUM, FraudSignalType.CHAT_SPAM, "CHAT_SPAM");
            
            // 1. Mute creator for 10 minutes (System action, using an admin as moderator if available)
            User moderator = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == com.joinlivora.backend.user.Role.ADMIN)
                    .findFirst()
                    .orElse(null);
            
            if (moderator != null) {
                chatModerationService.muteUser(userId, moderator.getId(), Duration.ofMinutes(10), null);
            }
            
            // 2. Disconnect WebSocket (send DISCONNECT event)
            userRepository.findById(userId).ifPresent(user -> {
                RealtimeMessage disconnectMessage = RealtimeMessage.builder()
                        .type("DISCONNECT")
                        .payload(Map.of("type", "Your account has been muted for 10 minutes due to spamming."))
                        .timestamp(Instant.now())
                        .build();
                messagingTemplate.convertAndSendToUser(user.getId().toString(), "/queue/notifications", disconnectMessage);
                log.info("SECURITY [chat_spam]: Sent disconnect message to creator {}", user.getEmail());
            });

        } else if ("suspicious".equalsIgnoreCase(rule.getAction())) {
            log.warn("SECURITY [velocity_anomaly]: Suspicious activity detected for creator {}: {}", userId, baseReason);
            saveSignal(userId, FraudDecisionLevel.MEDIUM, FraudSignalType.VELOCITY_WARNING, baseReason);
        } else if ("critical".equalsIgnoreCase(rule.getAction())) {
            log.error("SECURITY [velocity_anomaly]: Critical activity detected for creator {}: {}", userId, baseReason);
            saveSignal(userId, FraudDecisionLevel.HIGH, FraudSignalType.VELOCITY_WARNING, baseReason);
            escalateUser(userId, baseReason);
        }
    }

    private void saveSignal(Long userId, FraudDecisionLevel riskLevel, FraudSignalType type, String reason) {
        RuleFraudSignal signal = RuleFraudSignal.builder()
                .userId(userId)
                .riskLevel(riskLevel)
                .source(FraudSource.SYSTEM)
                .type(type)
                .reason(reason)
                .createdAt(Instant.now())
                .build();
        fraudSignalRepository.save(signal);
    }

    private void escalateUser(Long userId, String reason) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFraudRiskLevel(FraudRiskLevel.HIGH);
            userRepository.save(user);

            UserRiskState state = userRiskStateRepository.findById(userId)
                    .orElse(UserRiskState.builder().userId(userId).build());

            state.setCurrentRisk(FraudDecisionLevel.HIGH);
            state.setPaymentLocked(true);
            state.setBlockedUntil(Instant.now().plus(24, ChronoUnit.HOURS));
            userRiskStateRepository.save(state);

            log.warn("SECURITY [fraud_escalation]: User {} escalated to HIGH risk due to velocity anomaly: {}", user.getEmail(), reason);
        });
    }
}
