package com.joinlivora.transcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Transcode Heartbeat Watchdog — detects stale worker slots and re-queues orphaned jobs.
 *
 * <h2>Failure Scenario Handled</h2>
 * <p>When a transcode worker crashes (OOM, GPU fault, pod restart) mid-stream:
 * <ol>
 *   <li>The worker's FFmpeg process is killed by the OS</li>
 *   <li>The {@code stream:worker:{streamId}} heartbeat key (TTL = 15 s) naturally expires</li>
 *   <li>This watchdog detects the expired key by cross-referencing the
 *       {@code stream:active-jobs} set with live heartbeat keys</li>
 *   <li>The orphaned job is re-pushed to {@code stream:transcode:jobs} so another worker claims it</li>
 * </ol>
 *
 * <h2>Key Redis Keys Used</h2>
 * <ul>
 *   <li>{@code stream:active-jobs}         — Set of streamId strings for all actively transcoding streams</li>
 *   <li>{@code stream:worker:{streamId}}   — String "alive" with TTL; refreshed every 5 s by the worker</li>
 *   <li>{@code stream:transcode:jobs}      — List (BLPOP queue) for incoming transcode jobs</li>
 *   <li>{@code stream:transcode:job:{streamId}} — Hash storing original job payload for re-queue</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscodeHeartbeatWatchdog {

    private static final String ACTIVE_JOBS_KEY   = "stream:active-jobs";
    private static final String HEARTBEAT_PREFIX   = "stream:worker:";
    private static final String JOB_QUEUE_KEY      = "stream:transcode:jobs";
    private static final String JOB_PAYLOAD_PREFIX = "stream:transcode:job:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;
    private final TranscodeProperties properties;

    /**
     * Runs every 8 seconds. Detects streams whose heartbeat key has expired
     * (worker crashed) and re-enqueues the original job payload so a healthy
     * worker can claim it.
     *
     * <p>Running interval (8 s) &lt; heartbeat TTL (15 s) ensures every crash is
     * detected within at most 1 watchdog cycle after the TTL expires.
     */
    @Scheduled(fixedDelay = 8_000)
    public void scanForOrphanedJobs() {
        Set<String> activeJobs;
        try {
            activeJobs = redisTemplate.opsForSet().members(ACTIVE_JOBS_KEY);
        } catch (Exception ex) {
            log.warn("[WATCHDOG] Cannot read active-jobs set: {}", ex.getMessage());
            return;
        }

        if (activeJobs == null || activeJobs.isEmpty()) {
            return;
        }

        for (String streamIdStr : activeJobs) {
            try {
                String heartbeatKey = HEARTBEAT_PREFIX + streamIdStr;
                Boolean alive = redisTemplate.hasKey(heartbeatKey);

                if (!Boolean.TRUE.equals(alive)) {
                    handleOrphanedJob(streamIdStr);
                }
            } catch (Exception ex) {
                log.warn("[WATCHDOG] Error checking heartbeat for stream {}: {}",
                        streamIdStr, ex.getMessage());
            }
        }
    }

    /**
     * Registers a stream as actively transcoding and stores its job payload
     * for potential re-queue. Called by {@link TranscodeWorkerService} when a
     * job is claimed.
     */
    public void registerActiveJob(UUID streamId, TranscodeJob job) {
        try {
            String payloadJson = objectMapper.writeValueAsString(job);
            redisTemplate.opsForValue().set(JOB_PAYLOAD_PREFIX + streamId, payloadJson);
            redisTemplate.opsForSet().add(ACTIVE_JOBS_KEY, streamId.toString());
            log.debug("[WATCHDOG] Registered active job for stream {}", streamId);
        } catch (Exception ex) {
            log.warn("[WATCHDOG] Failed to register active job for stream {}: {}",
                    streamId, ex.getMessage());
        }
    }

    /**
     * Deregisters a stream from the active-jobs tracking. Called when a job
     * completes normally (via stop signal or FFmpeg exit).
     */
    public void deregisterActiveJob(UUID streamId) {
        try {
            redisTemplate.opsForSet().remove(ACTIVE_JOBS_KEY, streamId.toString());
            redisTemplate.delete(JOB_PAYLOAD_PREFIX + streamId);
            log.debug("[WATCHDOG] Deregistered job for stream {}", streamId);
        } catch (Exception ex) {
            log.warn("[WATCHDOG] Failed to deregister job for stream {}: {}",
                    streamId, ex.getMessage());
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void handleOrphanedJob(String streamIdStr) {
        log.warn("[WATCHDOG] Orphaned stream detected: {} — heartbeat expired. Re-queuing job.",
                streamIdStr);

        String payloadJson = null;
        try {
            payloadJson = redisTemplate.opsForValue().get(JOB_PAYLOAD_PREFIX + streamIdStr);
        } catch (Exception ex) {
            log.warn("[WATCHDOG] Cannot retrieve payload for stream {}: {}", streamIdStr, ex.getMessage());
        }

        if (payloadJson == null) {
            log.warn("[WATCHDOG] No stored payload for orphaned stream {} — cannot re-queue. "
                    + "Removing from active-jobs set.", streamIdStr);
            safeRemoveFromActiveJobs(streamIdStr);
            return;
        }

        // Validate the payload is still a valid job before re-queuing
        try {
            TranscodeJob job = objectMapper.readValue(payloadJson, TranscodeJob.class);

            // Guard: do not re-queue if stream has been live for >6 hours (runaway guard)
            if (job.getQueuedAt() != null) {
                long ageMs = Instant.now().toEpochMilli() - job.getQueuedAt();
                if (ageMs > 6 * 3600 * 1000L) {
                    log.warn("[WATCHDOG] Stream {} job is >6h old (age={}ms) — skipping re-queue.",
                            streamIdStr, ageMs);
                    safeRemoveFromActiveJobs(streamIdStr);
                    return;
                }
            }

            // Re-push job to the front of the queue (LPUSH = higher priority than RPUSH)
            redisTemplate.opsForList().leftPush(JOB_QUEUE_KEY, payloadJson);
            // Remove from active-jobs — the claiming worker will re-register it
            safeRemoveFromActiveJobs(streamIdStr);

            log.info("[WATCHDOG] Re-queued orphaned transcode job for stream {} (streamId={}, creatorId={})",
                    streamIdStr, job.getStreamId(), job.getCreatorId());

        } catch (Exception ex) {
            log.error("[WATCHDOG] Failed to re-queue orphaned stream {}: {}", streamIdStr, ex.getMessage());
            safeRemoveFromActiveJobs(streamIdStr);
        }
    }

    private void safeRemoveFromActiveJobs(String streamIdStr) {
        try {
            redisTemplate.opsForSet().remove(ACTIVE_JOBS_KEY, streamIdStr);
            redisTemplate.delete(JOB_PAYLOAD_PREFIX + streamIdStr);
        } catch (Exception ex) {
            log.warn("[WATCHDOG] Failed to clean up active-jobs for {}: {}", streamIdStr, ex.getMessage());
        }
    }
}
