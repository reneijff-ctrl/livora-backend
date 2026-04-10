package com.joinlivora.backend.abuse;

import com.joinlivora.backend.abuse.model.AbuseEvent;
import com.joinlivora.backend.abuse.model.AbuseEventType;
import com.joinlivora.backend.abuse.repository.AbuseEventRepository;
import com.joinlivora.backend.analytics.AnalyticsEventRepository;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.fraud.FraudScoringService;
import com.joinlivora.backend.fraud.model.FraudRiskLevel;
import com.joinlivora.backend.fraud.model.FraudRiskResult;
import com.joinlivora.backend.monetization.TipRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for detecting and handling abusive behavior.
 */
@Service
@Slf4j
public class AbuseDetectionService {
    private final AbuseEventRepository abuseEventRepository;
    private final FraudScoringService fraudRiskService;
    private final TipRepository tipRepository;
    private final AnalyticsEventRepository analyticsEventRepository;

    private final StringRedisTemplate redisTemplate;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.joinlivora.backend.resilience.RedisCircuitBreakerService redisCircuitBreaker;

    private static final String SOFTBLOCK_USER_PREFIX = "abuse:softblock:user:";
    private static final String SOFTBLOCK_IP_PREFIX = "abuse:softblock:ip:";
    private static final Duration SOFTBLOCK_DURATION = Duration.ofHours(1);

    public AbuseDetectionService(
            AbuseEventRepository abuseEventRepository,
            @org.springframework.context.annotation.Lazy FraudScoringService fraudRiskService,
            TipRepository tipRepository,
            AnalyticsEventRepository analyticsEventRepository,
            StringRedisTemplate redisTemplate
    ) {
        this.abuseEventRepository = abuseEventRepository;
        this.fraudRiskService = fraudRiskService;
        this.tipRepository = tipRepository;
        this.analyticsEventRepository = analyticsEventRepository;
        this.redisTemplate = redisTemplate;
    }

    private static final int RAPID_TIPPING_LIMIT = 10;
    private static final int MESSAGE_SPAM_LIMIT = 30;
    private static final int LOGIN_BRUTE_FORCE_LIMIT = 5;
    private static final int SUSPICIOUS_API_USAGE_LIMIT = 50;
    private static final int MULTI_ACCOUNT_BEHAVIOR_LIMIT = 3;

    /**
     * Tracks an abuse event and checks against thresholds.
     *
     * @param userId      The ID of the creator (optional)
     * @param ipAddress   The IP address associated with the event
     * @param eventType   The type of abuse event
     * @param description A description of the event
     */
    @Transactional
    public void trackEvent(UUID userId, String ipAddress, AbuseEventType eventType, String description) {
        log.info("Tracking abuse event: type={}, creator={}, ip={}", eventType, userId, maskIp(ipAddress));

        AbuseEvent event = AbuseEvent.builder()
                .userId(userId)
                .ipAddress(ipAddress)
                .eventType(eventType)
                .description(description)
                .build();
        abuseEventRepository.save(event);

        checkThresholds(userId, ipAddress, eventType);
    }

    /**
     * Checks if the creator is sending tips too rapidly (more than 5 in 30 seconds).
     *
     * @param userId    The ID of the creator
     * @param ipAddress The IP address of the request
     */
    @Transactional
    public void checkRapidTipping(UUID userId, String ipAddress) {
        Instant thirtySecondsAgo = Instant.now().minus(Duration.ofSeconds(30));
        long tipCount = tipRepository.countBySenderUserId_IdAndCreatedAtAfter(requireLegacyUserId(userId), thirtySecondsAgo);

        log.debug("Checking rapid tipping for creator {}: {} tips in last 30 seconds", userId, tipCount);

        if (tipCount > 5) {
            log.warn("RAPID_TIPPING detected for creator {} and IP {}: {} tips in last 30 seconds", userId, maskIp(ipAddress), tipCount);
            
            trackEvent(userId, ipAddress, AbuseEventType.RAPID_TIPPING, "More than 5 tips in 30 seconds (count: " + tipCount + ")");
            
            FraudRiskResult result = new FraudRiskResult(FraudRiskLevel.LOW, 20, List.of("RAPID_TIPPING: > 5 tips in 30s (actual: " + tipCount + ")"));
            fraudRiskService.recordDecision(userId, null, null, result);
        }
    }

    /**
     * Checks if the creator is spamming messages (more than 10 in 15 seconds).
     *
     * @param userId    The ID of the creator
     * @param ipAddress The IP address of the request
     */
    @Transactional
    public void checkMessageSpam(UUID userId, String ipAddress) {
        Instant fifteenSecondsAgo = Instant.now().minus(Duration.ofSeconds(15));
        long messageCount = analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                requireLegacyUserId(userId),
                AnalyticsEventType.CHAT_MESSAGE_SENT,
                fifteenSecondsAgo
        );

        log.debug("Checking message spam for creator {}: {} messages in last 15 seconds", userId, messageCount);

        if (messageCount > 10) {
            log.warn("MESSAGE_SPAM detected for creator {} and IP {}: {} messages in last 15 seconds", userId, maskIp(ipAddress), messageCount);

            trackEvent(userId, ipAddress, AbuseEventType.MESSAGE_SPAM, "More than 10 messages in 15 seconds (count: " + messageCount + ")");

            FraudRiskResult result = new FraudRiskResult(FraudRiskLevel.LOW, 10, List.of("MESSAGE_SPAM: > 10 messages in 15s (actual: " + messageCount + ")"));
            fraudRiskService.recordDecision(userId, null, null, result);
        }
    }

    /**
     * Checks for login brute force attempts (5 failed logins in 10 minutes).
     *
     * @param userId    The ID of the creator
     * @param ipAddress The IP address of the request
     */
    @Transactional
    public void checkLoginBruteForce(UUID userId, String ipAddress) {
        Instant tenMinutesAgo = Instant.now().minus(Duration.ofMinutes(10));
        long failedCount = analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                requireLegacyUserId(userId),
                AnalyticsEventType.USER_LOGIN_FAILED,
                tenMinutesAgo
        );

        log.debug("Checking login brute force for creator {}: {} failed logins in last 10 minutes", userId, failedCount);

        if (failedCount >= 5) {
            log.warn("LOGIN_BRUTE_FORCE detected for creator {} and IP {}: {} failed logins in last 10 minutes", userId, maskIp(ipAddress), failedCount);

            trackEvent(userId, ipAddress, AbuseEventType.LOGIN_BRUTE_FORCE, "5 or more failed logins in 10 minutes (count: " + failedCount + ")");

            FraudRiskResult result = new FraudRiskResult(FraudRiskLevel.MEDIUM, 40, List.of("LOGIN_BRUTE_FORCE: >= 5 failed logins in 10m (actual: " + failedCount + ")"));
            fraudRiskService.recordDecision(userId, null, null, result);
        }
    }

    private void checkThresholds(UUID userId, String ipAddress, AbuseEventType eventType) {
        Instant oneMinuteAgo = Instant.now().minus(Duration.ofMinutes(1));
        Instant tenMinutesAgo = Instant.now().minus(Duration.ofMinutes(10));

        long count;
        int limit;
        Instant since;

        switch (eventType) {
            case RAPID_TIPPING -> {
                limit = RAPID_TIPPING_LIMIT;
                since = oneMinuteAgo;
            }
            case MESSAGE_SPAM -> {
                limit = MESSAGE_SPAM_LIMIT;
                since = oneMinuteAgo;
            }
            case LOGIN_BRUTE_FORCE -> {
                limit = LOGIN_BRUTE_FORCE_LIMIT;
                since = tenMinutesAgo;
            }
            case SUSPICIOUS_API_USAGE -> {
                limit = SUSPICIOUS_API_USAGE_LIMIT;
                since = oneMinuteAgo;
            }
            case MULTI_ACCOUNT_BEHAVIOR -> {
                limit = MULTI_ACCOUNT_BEHAVIOR_LIMIT;
                since = Instant.now().minus(Duration.ofMinutes(10));
            }
            default -> {
                return;
            }
        }

        if (userId != null) {
            count = abuseEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(userId, eventType, since);
            log.debug("Checking threshold for creator {}: type={}, limit={}, count={}", userId, eventType, limit, count);
            if (count >= limit) {
                log.warn("Abuse threshold exceeded for creator {}: type={}, count={}", userId, eventType, count);
                escalate(userId, eventType, count);
                applySoftBlock(userId, ipAddress);
            }
        } else if (ipAddress != null) {
            count = abuseEventRepository.countByIpAddressAndEventTypeAndCreatedAtAfter(ipAddress, eventType, since);
            log.debug("Checking threshold for IP {}: type={}, limit={}, count={}", maskIp(ipAddress), eventType, limit, count);
            if (count >= limit) {
                log.warn("Abuse threshold exceeded for IP {}: type={}, count={}", maskIp(ipAddress), eventType, count);
                applySoftBlock(null, ipAddress);
            }
        }
    }

    private void escalate(UUID userId, AbuseEventType eventType, long count) {
        log.info("Escalating abuse event to FraudScoringService for creator {}", userId);
        
        // Map event type to appropriate score for restriction levels
        // 80 -> CHAT_MUTE, 70 -> TIP_LIMIT
        int score = (eventType == AbuseEventType.MESSAGE_SPAM) ? 80 : 70;
        
        FraudRiskResult result = new FraudRiskResult(
                FraudRiskLevel.HIGH,
                score,
                List.of("Abuse detected: " + eventType + " (count: " + count + ")")
        );
        fraudRiskService.recordDecision(userId, null, null, result);
    }

    /**
     * Writes soft-block keys to Redis (FAIL-OPEN: if Redis is down, block is skipped and
     * the circuit breaker probes recovery on the next call).
     */
    private void applySoftBlock(UUID userId, String ipAddress) {
        log.info("Applying soft block for creator {} and IP {}", userId, maskIp(ipAddress));
        // Uses execute() — no isOpen() pre-check so HALF_OPEN probe is not blocked.
        // Fail-open: if Redis is unavailable, the soft-block is skipped gracefully.
        if (userId != null) {
            final String userKey = SOFTBLOCK_USER_PREFIX + userId;
            boolean ok = (redisCircuitBreaker != null)
                    ? Boolean.TRUE.equals(redisCircuitBreaker.execute(
                            () -> { redisTemplate.opsForValue().set(userKey, "1", SOFTBLOCK_DURATION); return Boolean.TRUE; },
                            Boolean.FALSE,
                            "redis:abuse:softblock-user"))
                    : execute(() -> redisTemplate.opsForValue().set(userKey, "1", SOFTBLOCK_DURATION));
            if (ok) log.debug("Redis soft-block set for user key={}", userKey);
            else log.warn("ABUSE REDIS ERROR: Redis circuit OPEN — soft block skipped for user={}", userId);
        }
        if (ipAddress != null) {
            final String ipKey = SOFTBLOCK_IP_PREFIX + ipAddress;
            boolean ok = (redisCircuitBreaker != null)
                    ? Boolean.TRUE.equals(redisCircuitBreaker.execute(
                            () -> { redisTemplate.opsForValue().set(ipKey, "1", SOFTBLOCK_DURATION); return Boolean.TRUE; },
                            Boolean.FALSE,
                            "redis:abuse:softblock-ip"))
                    : execute(() -> redisTemplate.opsForValue().set(ipKey, "1", SOFTBLOCK_DURATION));
            if (ok) log.debug("Redis soft-block set for IP key={}", ipKey);
            else log.warn("ABUSE REDIS ERROR: Redis circuit OPEN — soft block skipped for ip={}", maskIp(ipAddress));
        }
    }

    /** Thin helper so lambda captures don't need to handle checked exceptions. */
    private boolean execute(Runnable r) {
        try { r.run(); return true; }
        catch (Exception e) { log.warn("ABUSE REDIS ERROR: {}", e.getMessage()); return false; }
    }

    private long requireLegacyUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User UUID cannot be null for legacy conversion.");
        }
        if (userId.getMostSignificantBits() != 0L) {
            throw new IllegalArgumentException(
                "Invalid user UUID for legacy conversion. Expected MSB=0 bridge UUID but received: " + userId
            );
        }
        return userId.getLeastSignificantBits();
    }

    private String maskIp(String ip) {
        if (ip == null) return null;
        return ip.replaceAll("(\\d+)\\.(\\d+)\\..*", "$1.$2.***.***");
    }

    /**
     * Checks if a creator or IP is currently soft-blocked.
     *
     * <p><strong>SECURITY — FAIL-CLOSED:</strong> if Redis is unavailable, this method returns
     * {@code true} (assume blocked). Granting access without being able to verify a soft-block
     * would allow abuse patterns to bypass detection. This is intentionally conservative.
     * The circuit breaker still attempts a HALF_OPEN probe via {@code execute()}, so as soon as
     * Redis recovers, normal operation resumes automatically.</p>
     *
     * @param userId    The UUID of the user to check
     * @param ipAddress The IP address to check
     * @return true if soft-blocked or if Redis is unavailable (fail-CLOSED)
     */
    public boolean isSoftBlocked(UUID userId, String ipAddress) {
        if (userId != null) {
            final String userKey = SOFTBLOCK_USER_PREFIX + userId;
            Boolean blocked = (redisCircuitBreaker != null)
                    ? redisCircuitBreaker.execute(
                            () -> redisTemplate.hasKey(userKey),
                            Boolean.TRUE,  // FAIL-CLOSED: assume blocked if Redis is down
                            "redis:abuse:is-softblocked-user")
                    : redisTemplate.hasKey(userKey);
            if (Boolean.TRUE.equals(blocked)) {
                return true;
            }
        }
        if (ipAddress != null) {
            final String ipKey = SOFTBLOCK_IP_PREFIX + ipAddress;
            Boolean blocked = (redisCircuitBreaker != null)
                    ? redisCircuitBreaker.execute(
                            () -> redisTemplate.hasKey(ipKey),
                            Boolean.TRUE,  // FAIL-CLOSED: assume blocked if Redis is down
                            "redis:abuse:is-softblocked-ip")
                    : redisTemplate.hasKey(ipKey);
            if (Boolean.TRUE.equals(blocked)) {
                return true;
            }
        }
        return false;
    }
}
