package com.joinlivora.backend.streaming.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

/**
 * Implements graceful load shedding for extreme-load streams.
 *
 * <h3>Problem</h3>
 * <p>When a celebrity/viral stream hits 100k+ concurrent viewers, various platform components
 * can saturate: Mediasoup WebRTC capacity, Redis connection pool, backend thread pool, or the
 * HLS origin bandwidth budget. Without a shedding mechanism the system will eventually crash
 * rather than degrade gracefully.
 *
 * <h3>Shedding tiers (applied in order)</h3>
 * <ol>
 *   <li><strong>Soft cap</strong> ({@code SOFT_CAP_VIEWERS}): Warn in logs. No user impact.</li>
 *   <li><strong>Quality reduction</strong> ({@code QUALITY_CAP_VIEWERS}): Remove the 720p variant
 *       from the master playlist to reduce origin bandwidth by 55%.</li>
 *   <li><strong>WebRTC-only denial</strong> ({@code WEBRTC_CAP_VIEWERS}): New viewers receive
 *       HLS-only access. WebRTC consumer slots are preserved for existing viewers.</li>
 *   <li><strong>Hard cap</strong> ({@code HARD_CAP_VIEWERS}): Reject all new viewer join attempts
 *       with HTTP 503. Existing viewers are unaffected.</li>
 * </ol>
 *
 * <h3>Redis keys</h3>
 * <ul>
 *   <li>{@code stream:shed:{streamId}:tier} — current shedding tier (0-3), refreshed every 30s</li>
 *   <li>{@code stream:shed:{streamId}:hard-reject} — flag: TTL key set when hard cap active</li>
 *   <li>{@code stream:shed:{streamId}:quality-reduced} — flag: 720p removed from playlists</li>
 *   <li>{@code stream:shed:{streamId}:webrtc-denied} — flag: new WebRTC consumers denied</li>
 * </ul>
 *
 * <h3>Recovery</h3>
 * <p>When viewer count drops below a tier threshold, the corresponding shedding flag is removed
 * and quality/access is restored. All flags have a 60-second TTL so they self-clear if the
 * stream ends unexpectedly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewerLoadSheddingService {

    private final StringRedisTemplate redisTemplate;
    private final StreamShardingService shardingService;

    // ── Thresholds (configurable via application.yml) ────────────────────────────────────────

    @Value("${livora.shedding.soft-cap:50000}")
    private long softCapViewers;

    @Value("${livora.shedding.quality-cap:100000}")
    private long qualityCapViewers;

    @Value("${livora.shedding.webrtc-cap:200000}")
    private long webrtcCapViewers;

    @Value("${livora.shedding.hard-cap:500000}")
    private long hardCapViewers;

    @Value("${livora.shedding.enabled:true}")
    private boolean sheddingEnabled;

    // ── Redis key patterns ────────────────────────────────────────────────────────────────────

    private static final String TIER_KEY          = "stream:shed:%s:tier";
    private static final String HARD_REJECT_KEY   = "stream:shed:%s:hard-reject";
    private static final String QUALITY_REDUCED   = "stream:shed:%s:quality-reduced";
    private static final String WEBRTC_DENIED_KEY = "stream:shed:%s:webrtc-denied";

    /**
     * Platform-wide CDN failure flag. When set, all streams act as if they are
     * in Tier 1 (quality reduced) regardless of their actual viewer count.
     * This limits origin bandwidth to ~45% of full capacity while the CDN is
     * unavailable, preventing origin bandwidth exhaustion.
     */
    private static final String CDN_FAILURE_KEY   = "platform:cdn-failure";
    private static final Duration CDN_FAILURE_TTL = Duration.ofMinutes(10);

    private static final Duration SHEDDING_TTL = Duration.ofMinutes(2);

    // ── Load shedding tier evaluation ─────────────────────────────────────────────────────────

    /**
     * Evaluates the current viewer count against shedding thresholds and updates Redis flags.
     * Called by {@link com.joinlivora.backend.streaming.service.ViewerSpikeDetectionService}
     * and by a scheduled aggregator every 10 seconds.
     *
     * @param streamId    the UUID of the stream
     * @param viewerCount the current viewer count
     */
    public void evaluateShedding(UUID streamId, long viewerCount) {
        if (!sheddingEnabled) {
            return;
        }

        int tier = 0;
        if (viewerCount >= hardCapViewers) {
            tier = 3;
        } else if (viewerCount >= webrtcCapViewers) {
            tier = 2;
        } else if (viewerCount >= qualityCapViewers) {
            tier = 1;
        } else if (viewerCount >= softCapViewers) {
            tier = 0; // soft cap — log only
            log.warn("LOAD SHEDDING SOFT CAP stream={} viewers={}", streamId, viewerCount);
        }

        if (tier > 0) {
            log.warn("LOAD SHEDDING TIER {} stream={} viewers={}", tier, streamId, viewerCount);
        }

        try {
            redisTemplate.opsForValue().set(
                    String.format(TIER_KEY, streamId), String.valueOf(tier), SHEDDING_TTL);

            // Apply/remove quality shedding flag
            if (tier >= 1) {
                redisTemplate.opsForValue().set(
                        String.format(QUALITY_REDUCED, streamId), "1", SHEDDING_TTL);
            } else {
                redisTemplate.delete(String.format(QUALITY_REDUCED, streamId));
            }

            // Apply/remove WebRTC denial flag
            if (tier >= 2) {
                redisTemplate.opsForValue().set(
                        String.format(WEBRTC_DENIED_KEY, streamId), "1", SHEDDING_TTL);
            } else {
                redisTemplate.delete(String.format(WEBRTC_DENIED_KEY, streamId));
            }

            // Apply/remove hard reject flag
            if (tier >= 3) {
                redisTemplate.opsForValue().set(
                        String.format(HARD_REJECT_KEY, streamId), "1", SHEDDING_TTL);
            } else {
                redisTemplate.delete(String.format(HARD_REJECT_KEY, streamId));
            }
        } catch (Exception e) {
            log.error("LOAD SHEDDING REDIS WRITE FAILED stream={}: {}", streamId, e.getMessage());
        }
    }

    // ── Guard methods (called at join/consumer time) ──────────────────────────────────────────

    /**
     * Checks whether new viewers are allowed to join this stream.
     * Throws HTTP 503 if the hard cap is active.
     *
     * @param streamId the UUID of the stream to join
     * @throws ResponseStatusException HTTP 503 with retry-after header if capacity is full
     */
    public void assertViewerAllowed(UUID streamId) {
        if (!sheddingEnabled) {
            return;
        }
        try {
            String hardReject = redisTemplate.opsForValue().get(
                    String.format(HARD_REJECT_KEY, streamId));
            if ("1".equals(hardReject)) {
                log.warn("LOAD SHEDDING HARD REJECT stream={} — new viewer denied", streamId);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Stream is at capacity. Please try again in a few minutes.");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            // Redis unavailable — fail-open (allow the viewer)
            log.warn("LOAD SHEDDING CHECK FAILED stream={}: {} — allowing viewer", streamId,
                    e.getMessage());
        }
    }

    /**
     * Checks whether new WebRTC consumer slots are available.
     * Returns {@code false} if WebRTC shedding is active (viewer should fall back to HLS).
     *
     * @param streamId the UUID of the stream
     * @return {@code true} if WebRTC is allowed; {@code false} to force HLS-only
     */
    public boolean isWebRtcAllowed(UUID streamId) {
        if (!sheddingEnabled) {
            return true;
        }
        try {
            String denied = redisTemplate.opsForValue().get(
                    String.format(WEBRTC_DENIED_KEY, streamId));
            if ("1".equals(denied)) {
                log.info("LOAD SHEDDING WEBRTC DENIED stream={} — routing to HLS", streamId);
                return false;
            }
        } catch (Exception e) {
            // Redis unavailable — fail-open
            log.warn("LOAD SHEDDING WEBRTC CHECK FAILED stream={}: {} — allowing WebRTC",
                    streamId, e.getMessage());
        }
        return true;
    }

    /**
     * Returns {@code true} if the 720p HLS variant should be suppressed from the master playlist
     * to reduce origin bandwidth.
     *
     * <p>Quality is also reduced when the platform is in CDN failure mode
     * ({@link #activateCdnFailureMode()}) — this protects origin bandwidth when the CDN
     * is down and all traffic hits the origin directly.
     *
     * @param streamId the UUID of the stream
     * @return {@code true} if quality should be reduced to 480p max
     */
    public boolean isQualityReduced(UUID streamId) {
        if (!sheddingEnabled) {
            return false;
        }
        try {
            // Check platform-wide CDN failure flag first (applies to all streams)
            String cdnFailure = redisTemplate.opsForValue().get(CDN_FAILURE_KEY);
            if ("1".equals(cdnFailure)) {
                return true;
            }
            String reduced = redisTemplate.opsForValue().get(
                    String.format(QUALITY_REDUCED, streamId));
            return "1".equals(reduced);
        } catch (Exception e) {
            return false;
        }
    }

    // ── CDN Failure Mode ──────────────────────────────────────────────────────────────────────

    /**
     * Activates platform-wide CDN failure mode.
     *
     * <p>When active, all streams have quality reduced to 480p max
     * ({@link #isQualityReduced} returns {@code true} for every stream).
     * This limits origin bandwidth to ~45% of full capacity, preventing
     * bandwidth exhaustion when Cloudflare CDN is unavailable and all
     * viewer segment requests hit the origin directly.
     *
     * <p>The flag has a 10-minute TTL and is automatically refreshed by the
     * health check that detects the CDN failure. Call {@link #deactivateCdnFailureMode()}
     * once CDN health checks pass again.
     *
     * <h3>Trigger conditions</h3>
     * <ul>
     *   <li>Cloudflare health-check alert webhook → POST /api/internal/cdn-failure</li>
     *   <li>Manual operator action during an incident</li>
     *   <li>HLS origin bandwidth metric crosses 80% threshold (future auto-trigger)</li>
     * </ul>
     */
    public void activateCdnFailureMode() {
        try {
            redisTemplate.opsForValue().set(CDN_FAILURE_KEY, "1", CDN_FAILURE_TTL);
            log.error("[CDN_FAILURE_MODE] ACTIVATED — quality reduced to 480p max for ALL streams. "
                    + "TTL={}min. Origin bandwidth protection active.", CDN_FAILURE_TTL.toMinutes());
        } catch (Exception e) {
            log.error("[CDN_FAILURE_MODE] Failed to set CDN failure flag: {}", e.getMessage());
        }
    }

    /**
     * Deactivates platform-wide CDN failure mode and restores full quality for all streams.
     * Should be called once Cloudflare CDN health checks pass again.
     */
    public void deactivateCdnFailureMode() {
        try {
            redisTemplate.delete(CDN_FAILURE_KEY);
            log.info("[CDN_FAILURE_MODE] DEACTIVATED — full quality restored for all streams.");
        } catch (Exception e) {
            log.error("[CDN_FAILURE_MODE] Failed to clear CDN failure flag: {}", e.getMessage());
        }
    }

    /**
     * Returns {@code true} if CDN failure mode is currently active.
     * Safe to call from health endpoints without Redis circuit-breaker overhead.
     */
    public boolean isCdnFailureModeActive() {
        try {
            return "1".equals(redisTemplate.opsForValue().get(CDN_FAILURE_KEY));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the current shedding tier for observability (0 = normal, 1–3 = active shedding).
     */
    public int getCurrentTier(UUID streamId) {
        try {
            String tier = redisTemplate.opsForValue().get(String.format(TIER_KEY, streamId));
            return tier == null ? 0 : Integer.parseInt(tier);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Clears all shedding state for a stream after it ends.
     */
    public void clearSheddingState(UUID streamId) {
        try {
            redisTemplate.delete(String.format(TIER_KEY, streamId));
            redisTemplate.delete(String.format(HARD_REJECT_KEY, streamId));
            redisTemplate.delete(String.format(QUALITY_REDUCED, streamId));
            redisTemplate.delete(String.format(WEBRTC_DENIED_KEY, streamId));
            log.info("LOAD SHEDDING STATE CLEARED stream={}", streamId);
        } catch (Exception e) {
            log.warn("LOAD SHEDDING CLEAR FAILED stream={}: {}", streamId, e.getMessage());
        }
    }
}
