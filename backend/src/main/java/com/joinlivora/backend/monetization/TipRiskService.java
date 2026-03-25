package com.joinlivora.backend.monetization;

import com.joinlivora.backend.abuse.AbuseDetectionService;
import com.joinlivora.backend.abuse.RestrictionService;
import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.aml.service.AMLRulesEngine;
import com.joinlivora.backend.chargeback.ChargebackService;
import com.joinlivora.backend.exception.TrustChallengeException;
import com.joinlivora.backend.fraud.FraudScoringService;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.exception.HighFraudRiskException;
import com.joinlivora.backend.fraud.model.*;
import com.joinlivora.backend.fraud.service.EnforcementService;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.fraud.service.TrustEvaluationService;
import com.joinlivora.backend.fraud.service.VelocityTrackerService;
import com.joinlivora.backend.payment.PaymentService;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class TipRiskService {

    private final TipValidationService tipValidationService;
    private final PaymentService paymentService;
    private final VelocityTrackerService velocityTrackerService;
    private final TrustEvaluationService trustEvaluationService;
    private final FraudDetectionService fraudDetectionService;
    private final AMLRulesEngine amlRulesEngine;
    private final FraudScoringService fraudRiskService;
    private final AbuseDetectionService abuseDetectionService;
    private final RestrictionService restrictionService;
    private final EnforcementService enforcementService;
    private final StringRedisTemplate redisTemplate;
    private final Environment environment;
    private final TipRepository tipRepository;
    private final ChargebackService chargebackService;
    private final AdminRealtimeEventService adminRealtimeEventService;

    private static final AtomicInteger tipCollisionCounter = new AtomicInteger(0);

    public void validateTippingAccess(UUID userId, BigDecimal amount) {
        restrictionService.validateTippingAccess(userId, amount);
    }

    public RiskLevel checkPaymentLock(User user, BigDecimal amount, String ipAddress, String country, String userAgent, String fingerprintHash) {
        return paymentService.checkPaymentLock(user, amount, ipAddress, country, userAgent, fingerprintHash);
    }

    public void trackVelocity(Long userId) {
        velocityTrackerService.trackAction(userId, VelocityActionType.TIP);
    }

    public void checkRapidTipping(UUID userId, String ipAddress) {
        abuseDetectionService.checkRapidTipping(userId, ipAddress);
    }

    public void validateStripeTip(User user, BigDecimal amount) {
        tipValidationService.validateStripeTip(user, amount);
    }

    public void validateTokenTip(User user, long amount, UUID roomId) {
        tipValidationService.validateTokenTip(user, amount, roomId);
    }

    public void recordFraudDecision(UUID userId, UUID roomId, Long tipId, FraudRiskResult risk) {
        fraudRiskService.recordDecision(userId, roomId, tipId, risk);
    }

    public void recordFraudIncident(UUID userId, String summary, Map<String, Object> details) {
        enforcementService.recordFraudIncident(userId, summary, details);
    }

    public void evaluateAMLRules(User creator, BigDecimal amount) {
        amlRulesEngine.evaluateRules(creator, amount);
    }

    public void checkSuspiciousTippingPatterns(User tipper, User creator, BigDecimal amount, String tipperIp, String tipperFingerprint) {
        if (isDevProfile()) return;

        // Same logic as in TipOrchestrationService.java
        // 4. Multiple users tip same creator in 2 minutes
        String redisKey = "tip-users:" + creator.getId();
        redisTemplate.opsForSet().add(redisKey, tipper.getId().toString());
        redisTemplate.expire(redisKey, Duration.ofMinutes(2));
        Long uniqueTippers = redisTemplate.opsForSet().size(redisKey);
        if (uniqueTippers != null && uniqueTippers > 1) {
            fraudRiskService.recordSignal(FraudSignalType.SUSPICIOUS_TIP_PATTERN, tipper.getId(), creator.getId(), amount);
            log.debug("SUSPICIOUS_TIP: Multiple users tipping creator {} within 2m window", creator.getId());
        }
        
        // 5. New account tipping
        if (tipper.getCreatedAt() != null) {
            Instant now = Instant.now();
            if (tipper.getCreatedAt().isAfter(now.minus(24, ChronoUnit.HOURS))) {
                fraudRiskService.recordSignal(FraudSignalType.NEW_ACCOUNT_TIPPING_HIGH, tipper.getId(), creator.getId(), amount);
                log.warn("SUSPICIOUS_TIP [HIGH]: New account (age < 24h) {} tipped creator {}", tipper.getEmail(), creator.getId());

                // 6. New account tipping cluster detection (5+ accounts < 24h in 10m)
                String clusterKey = "cluster:" + creator.getId();
                long nowMs = now.toEpochMilli();
                Boolean added = redisTemplate.opsForZSet().add(clusterKey, tipper.getId().toString(), nowMs);
                
                redisTemplate.expire(clusterKey, Duration.ofMinutes(15));
                
                if (Boolean.TRUE.equals(added)) {
                    redisTemplate.opsForZSet().removeRangeByScore(clusterKey, 0, nowMs - (10 * 60 * 1000));
                    Long clusterSize = redisTemplate.opsForZSet().zCard(clusterKey);
                    if (clusterSize != null && clusterSize >= 5) {
                        fraudRiskService.recordSignal(FraudSignalType.NEW_ACCOUNT_TIP_CLUSTER, tipper.getId(), creator.getId(), amount);
                        log.warn("SUSPICIOUS_TIP: New account tipping cluster detected for creator {}. Unique new accounts in 10m: {}", creator.getId(), clusterSize);

                        adminRealtimeEventService.publishAbuseEvent(
                            "TIP_CLUSTER",
                            new UUID(0L, creator.getId()),
                            creator.getUsername(),
                            "New account tipping cluster detected"
                        );
                    }
                }
            } else if (tipper.getCreatedAt().isAfter(now.minus(7, ChronoUnit.DAYS))) {
                fraudRiskService.recordSignal(FraudSignalType.NEW_ACCOUNT_TIPPING_MEDIUM, tipper.getId(), creator.getId(), amount);
                log.info("SUSPICIOUS_TIP [MEDIUM]: New account (age < 7d) {} tipped creator {}", tipper.getEmail(), creator.getId());
            }
        }

        // 7. Rapid tip repeats from same user to same creator (6+ tips in 2m)
        String repeatKey = "rapid-tip:" + tipper.getId() + ":" + creator.getId();
        long nowMs = Instant.now().toEpochMilli();
        int counter = tipCollisionCounter.incrementAndGet() % 1000000;
        String member = tipper.getId() + ":" + nowMs + ":" + counter;
        redisTemplate.opsForZSet().add(repeatKey, member, nowMs);
        redisTemplate.opsForZSet().removeRangeByScore(repeatKey, 0, nowMs - (2 * 60 * 1000));
        redisTemplate.opsForZSet().removeRange(repeatKey, 0, -51);
        redisTemplate.expire(repeatKey, Duration.ofMinutes(5));
        Long repeatCount = redisTemplate.opsForZSet().zCard(repeatKey);
        if (repeatCount != null && repeatCount > 5) {
            fraudRiskService.recordSignal(FraudSignalType.RAPID_TIP_REPEATS, tipper.getId(), creator.getId(), amount);
            log.warn("SUSPICIOUS_TIP: Rapid tip repeats detected. User {} tipped creator {} more than 5 times in 2 minutes", tipper.getId(), creator.getId());
        }
    }

    public void evaluateTrust(User user, String ipAddress, String fingerprintHash) {
        RiskDecisionResult result = trustEvaluationService.evaluate(user, fingerprintHash, ipAddress);
        if (result.getDecision() == RiskDecision.BLOCK) {
            log.warn("SECURITY [trust_evaluation]: Blocked tip attempt for creator: {} from IP: {} with fingerprint: {}. ExplanationId: {}",
                    user.getEmail(), ipAddress, fingerprintHash, result.getExplanationId());
            fraudDetectionService.logFraudSignal(user.getId(), FraudDecisionLevel.HIGH, FraudSource.PAYMENT, FraudSignalType.TRUST_EVALUATION_BLOCK, "TRUST_EVALUATION_BLOCK");
            throw new AccessDeniedException("Action blocked due to security risk.");
        } else if (result.getDecision() == RiskDecision.REVIEW) {
            log.info("SECURITY [trust_evaluation]: Trust challenge required for tip for creator: {}. ExplanationId: {}", user.getEmail(), result.getExplanationId());
            
            if (isDevProfile()) {
                log.warn("DEV MODE: Bypassing trust REVIEW for tipping.");
                return;
            }
            
            throw new TrustChallengeException("Additional verification required to complete this action.");
        }
    }

    public FraudRiskResult calculateFraudRisk(User user, BigDecimal amount) {
        Instant fiveMinutesAgo = Instant.now().minus(Duration.ofMinutes(5));
        int tipsInLast5Minutes = (int) tipRepository.countBySenderUserId_IdAndCreatedAtAfter(user.getId(), fiveMinutesAgo);

        // Calculate total amount in short window (last 5 minutes)
        BigDecimal totalAmountShortWindow = tipRepository.aggregateTips(fiveMinutesAgo).stream()
                .filter(row -> row[0].equals(user.getId()))
                .map(row -> (BigDecimal) row[2])
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(amount);

        long accountAgeDays = Duration.between(user.getCreatedAt(), Instant.now()).toDays();

        // Check if IP or device has changed recently (comparing with last successful login)
        // Simplified check for now
        boolean ipOrDeviceChanged = false;

        UUID userUuid = new UUID(0L, user.getId());
        int previousChargebacks = (int) chargebackService.getChargebackCount(userUuid);

        return fraudRiskService.calculateRisk(
                userUuid,
                amount,
                tipsInLast5Minutes,
                totalAmountShortWindow,
                accountAgeDays,
                ipOrDeviceChanged,
                previousChargebacks
        );
    }

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }
}
