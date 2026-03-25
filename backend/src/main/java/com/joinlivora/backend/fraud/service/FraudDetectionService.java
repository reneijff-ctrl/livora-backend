package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.analytics.AnalyticsEventRepository;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.fraud.model.*;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service("fraudDetectionService")
@RequiredArgsConstructor
public class FraudDetectionService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final RuleFraudSignalRepository fraudSignalRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final UserRiskStateRepository userRiskStateRepository;

    @Transactional
    public FraudDecisionLevel evaluate(User user, String currentIp, String currentCountry) {
        log.info("Evaluating fraud risk for creator: {}, IP: {}, Country: {}", user.getEmail(), currentIp, currentCountry);

        Instant now = Instant.now();
        Long userId = user.getId();

        // 1. Run individual rules and persist signals immediately
        evaluatePaymentFailureRules(user, userId, now);
        evaluateVelocityRules(user, userId, now);
        evaluateIpRules(currentIp, user, userId, now);
        evaluateCountryRules(currentCountry, user, userId);

        // 2. Automatic escalation: 2 MEDIUM risk signals within 24h -> upgrade to HIGH
        // Also LOW -> MEDIUM if 2 FraudSignals within 24h
        performAutomaticEscalation(user, userId, now);

        // 3. Determine final decision based on signals in last 24h
        FraudDecisionLevel decision = getHighestSignalLevel(userId, now.minus(24, ChronoUnit.HOURS));
        
        // 4. Update creator risk level and state based on the evaluation
        updateUserRiskLevelAndState(user, decision, now, false);

        log.info("Fraud evaluation completed for creator {}: {}", user.getEmail(), decision);

        return decision;
    }

    private void evaluatePaymentFailureRules(User user, Long userId, Instant now) {
        // Rule: More than 3 failed payments in 24h = HIGH risk
        long failedPayments24h = paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(
                userId, false, now.minus(24, ChronoUnit.HOURS));
        if (failedPayments24h >= 3) {
            saveSignal(userId, FraudDecisionLevel.HIGH, FraudSource.PAYMENT, FraudSignalType.PAYMENT_FAILURE,
                    "More than 3 failed payments in 24h (Count: " + failedPayments24h + ")");
        }

        // Rule: >= 2 failed payments within 10 minutes = MEDIUM risk
        long failedPayments10m = paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(
                userId, false, now.minus(10, ChronoUnit.MINUTES));
        if (failedPayments10m >= 2) {
            saveSignal(userId, FraudDecisionLevel.MEDIUM, FraudSource.PAYMENT, FraudSignalType.PAYMENT_FAILURE,
                    "At least 2 failed payments within 10 minutes (Count: " + failedPayments10m + ")");
        }
    }

    private void evaluateVelocityRules(User user, Long userId, Instant now) {
        // Velocity Anomaly: >= 5 failed payments within 1 hour = HIGH risk
        long failedPayments1h = paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(
                userId, false, now.minus(1, ChronoUnit.HOURS));
        if (failedPayments1h >= 5) {
            saveSignal(userId, FraudDecisionLevel.HIGH, FraudSource.PAYMENT, FraudSignalType.VELOCITY_WARNING,
                    "VELOCITY_ANOMALY: At least 5 failed payments within 1 hour (Count: " + failedPayments1h + ")");
        }
    }

    private void evaluateIpRules(String currentIp, User user, Long userId, Instant now) {
        // Rule: Different IPs within 10 minutes = MEDIUM risk
        List<String> recentIps = analyticsEventRepository.findDistinctIpsByUserIdAndCreatedAtAfter(
                userId, now.minus(10, ChronoUnit.MINUTES));
        
        boolean differentIpDetected = recentIps.stream()
                .anyMatch(ip -> currentIp != null && !currentIp.equals(ip));
        
        if (differentIpDetected) {
            saveSignal(userId, FraudDecisionLevel.MEDIUM, FraudSource.LOGIN, FraudSignalType.IP_MISMATCH, "Different IPs detected within 10 minutes");
        }
    }

    private void evaluateCountryRules(String currentCountry, User user, Long userId) {
        // Rule: Country mismatch vs last login = MEDIUM risk
        analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(
                userId, AnalyticsEventType.USER_LOGIN_SUCCESS)
                .ifPresent(lastLogin -> {
                    String lastCountry = (String) lastLogin.getMetadata().get("country");
                    if (lastCountry != null && !lastCountry.isBlank() && currentCountry != null && 
                            !currentCountry.isBlank() && !currentCountry.equalsIgnoreCase(lastCountry)) {
                        saveSignal(userId, FraudDecisionLevel.MEDIUM, FraudSource.LOGIN, FraudSignalType.COUNTRY_MISMATCH, "COUNTRY_MISMATCH");
                    }
                });
    }

    private void performAutomaticEscalation(User user, Long userId, Instant now) {
        // Rule: LOW -> MEDIUM if 2 FraudSignals within 24h
        if (user.getFraudRiskLevel() == FraudRiskLevel.LOW) {
            long totalSignals24h = fraudSignalRepository.countByUserIdAndCreatedAtAfter(userId, now.minus(24, ChronoUnit.HOURS));
            if (totalSignals24h >= 2) {
                saveSignal(userId, FraudDecisionLevel.MEDIUM, FraudSource.SYSTEM, FraudSignalType.AUTOMATIC_ESCALATION,
                        "AUTOMATIC_ESCALATION: " + totalSignals24h + " signals within 24h (LOW -> MEDIUM)");
            }
        }

        // Rule: MEDIUM -> HIGH if 2 MEDIUM signals within 24h
        if (user.getFraudRiskLevel() != FraudRiskLevel.HIGH) {
            long mediumSignals24h = fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(
                    userId, FraudDecisionLevel.MEDIUM, now.minus(24, ChronoUnit.HOURS));
            if (mediumSignals24h >= 2) {
                saveSignal(userId, FraudDecisionLevel.HIGH, FraudSource.SYSTEM, FraudSignalType.AUTOMATIC_ESCALATION,
                        "AUTOMATIC_ESCALATION: " + mediumSignals24h + " MEDIUM signals within 24h");
            }
        }
    }

    public FraudDecisionLevel getHighestSignalLevel(Long userId, Instant since) {
        if (fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(userId, FraudDecisionLevel.HIGH, since) > 0) {
            return FraudDecisionLevel.HIGH;
        }
        if (fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(userId, FraudDecisionLevel.MEDIUM, since) > 0) {
            return FraudDecisionLevel.MEDIUM;
        }
        return FraudDecisionLevel.LOW;
    }

    private void updateUserRiskLevelAndState(User user, FraudDecisionLevel decision, Instant now, boolean force) {
        FraudRiskLevel newLevel = FraudRiskLevel.valueOf(decision.name());
        
        // 1. Sync User entity
        if (force || newLevel.ordinal() > user.getFraudRiskLevel().ordinal()) {
            if (newLevel.ordinal() > user.getFraudRiskLevel().ordinal()) {
                log.warn("SECURITY [fraud_escalation]: Upgrading risk level for creator {} from {} to {}",
                        user.getEmail(), user.getFraudRiskLevel(), newLevel);
            }
            user.setFraudRiskLevel(newLevel);
            userRepository.save(user);
        }

        // 2. Sync UserRiskState (Detailed state)
        Long userId = user.getId();
        UserRiskState state = userRiskStateRepository.findById(userId)
                .orElse(UserRiskState.builder()
                        .userId(userId)
                        .currentRisk(FraudDecisionLevel.LOW)
                        .build());
        
        state.setCurrentRisk(decision);
        
        if (decision == FraudDecisionLevel.HIGH) {
            state.setPaymentLocked(true);
            state.setBlockedUntil(now.plus(72, ChronoUnit.HOURS));
        } else if (force) {
            // If forced (manual override), we might want to unlock
            if (decision != FraudDecisionLevel.HIGH) {
                state.setPaymentLocked(false);
                state.setBlockedUntil(null);
            }
        }
        
        userRiskStateRepository.save(state);
    }

    @Transactional
    public void logFraudSignal(Long userId, FraudDecisionLevel riskLevel, FraudSource source, FraudSignalType type, String reason) {
        saveSignal(userId, riskLevel, source, type, reason);
    }

    private void saveSignal(Long userId, FraudDecisionLevel riskLevel, FraudSource source, FraudSignalType type, String reason) {
        RuleFraudSignal signal = RuleFraudSignal.builder()
                .userId(userId)
                .riskLevel(riskLevel)
                .source(source)
                .type(type)
                .reason(reason)
                .createdAt(Instant.now())
                .build();
        fraudSignalRepository.save(signal);
        log.warn("Fraud signal stored: {} (Type: {}, Level: {}, Source: {})", reason, type, riskLevel, source);
    }

    @Transactional
    public void recordChargeback(User user, String paymentIntentId) {
        log.warn("SECURITY [fraud_incident]: Recording chargeback signal for creator: {}. PaymentIntent: {}", user.getEmail(), paymentIntentId);
        
        Long userId = user.getId();
        
        // Log the signal, but let ChargebackService handle the definitive risk level based on counts
        saveSignal(userId, FraudDecisionLevel.MEDIUM, FraudSource.PAYMENT, FraudSignalType.CHARGEBACK, "CHARGEBACK_RECEIVED");
        
        // Note: Risk level update removed here to avoid conflict with new ChargebackService rules
    }

    @Transactional
    public void processCooldown(UserRiskState state) {
        log.info("Processing fraud cooldown for creator ID: {}", state.getUserId());
        
        boolean hasUnresolvedHighRisk = fraudSignalRepository.existsByUserIdAndRiskLevelAndResolvedFalse(state.getUserId(), FraudDecisionLevel.HIGH);
        
        if (!hasUnresolvedHighRisk) {
            Long userId = state.getUserId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found for ID: " + userId));

            saveSignal(state.getUserId(), FraudDecisionLevel.MEDIUM, FraudSource.SYSTEM, FraudSignalType.FRAUD_COOLDOWN,
                    "FRAUD_COOLDOWN: Block expired and no unresolved HIGH signals.");
            
            updateUserRiskLevelAndState(user, FraudDecisionLevel.MEDIUM, Instant.now(), true);
            
            log.info("Fraud cooldown applied for creator: {}. Risk set to MEDIUM, payments unlocked.", user.getEmail());
        }
    }

    @Transactional
    public void resolveSignal(UUID signalId, User admin) {
        log.info("Admin {} resolving fraud signal: {}", admin.getEmail(), signalId);
        
        RuleFraudSignal signal = fraudSignalRepository.findById(signalId)
                .orElseThrow(() -> new IllegalArgumentException("Fraud signal not found: " + signalId));
        
        signal.setResolved(true);
        signal.setResolvedBy(new UUID(0L, admin.getId()));
        signal.setResolvedAt(Instant.now());
        
        fraudSignalRepository.save(signal);
    }

    @Transactional
    public void overrideRiskLevel(User user, FraudRiskLevel newLevel, User admin) {
        log.info("Admin {} overriding risk level for creator {} to {}", admin.getEmail(), user.getEmail(), newLevel);

        Long userId = user.getId();

        // Rule: Prevent downgrade if unresolved HIGH-risk signals exist
        if (newLevel.ordinal() < user.getFraudRiskLevel().ordinal()) {
            boolean hasUnresolvedHighRisk = fraudSignalRepository.existsByUserIdAndRiskLevelAndResolvedFalse(userId, FraudDecisionLevel.HIGH);
            if (hasUnresolvedHighRisk) {
                log.warn("Admin {} attempted to downgrade creator {} risk from {} to {}, but unresolved HIGH-risk signals exist",
                        admin.getEmail(), user.getEmail(), user.getFraudRiskLevel(), newLevel);
                throw new IllegalStateException("Cannot downgrade risk level because unresolved HIGH-risk signals exist.");
            }
        }

        // Log override as RuleFraudSignal with source ADMIN
        saveSignal(userId, FraudDecisionLevel.valueOf(newLevel.name()), FraudSource.ADMIN, FraudSignalType.ADMIN_OVERRIDE,
                "ADMIN_OVERRIDE: Manual risk level update by admin " + admin.getEmail());

        // Update creator risk level and state
        updateUserRiskLevelAndState(user, FraudDecisionLevel.valueOf(newLevel.name()), Instant.now(), true);
    }

    @Transactional
    public void unblockUser(User user, User admin) {
        log.info("Admin {} unblocking creator {}", admin.getEmail(), user.getEmail());

        Long userId = user.getId();

        // Log unblock as RuleFraudSignal with source ADMIN
        saveSignal(userId, FraudDecisionLevel.MEDIUM, FraudSource.ADMIN, FraudSignalType.ADMIN_UNBLOCK,
                "ADMIN_UNBLOCK: User manually unblocked by admin " + admin.getEmail());

        // Force reset to MEDIUM risk and unlock
        updateUserRiskLevelAndState(user, FraudDecisionLevel.MEDIUM, Instant.now(), true);
    }

}
