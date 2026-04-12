package com.joinlivora.backend.streaming.transcode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes transcode lifecycle events to Redis so the external
 * transcode-worker service can start / stop GPU FFmpeg processes.
 *
 * <p>Two channels are used:
 * <ul>
 *   <li>{@code stream:transcode:jobs}  — Redis List (RPUSH / BLPOP).
 *       Payload: JSON object {@code {streamId, creatorId, rtmpUrl}}.</li>
 *   <li>{@code stream:transcode:stop}  — Redis Pub/Sub channel.
 *       Payload: plain UUID string of the stream to stop.</li>
 * </ul>
 *
 * <p>All writes are wrapped in a try/catch so a Redis outage never
 * prevents a creator from going live via WebRTC — the transcode worker
 * will simply miss the job and the creator's stream will be WebRTC-only
 * until the worker recovers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TranscodeJobPublisher {

    /** Redis List key consumed by the transcode-worker via BLPOP. */
    public static final String JOBS_LIST_KEY   = "stream:transcode:jobs";

    /** Redis Pub/Sub channel for stop signals. */
    public static final String STOP_CHANNEL    = "stream:transcode:stop";

    private final StringRedisTemplate redisTemplate;

    @Value("${livora.nginx.rtmp.host:nginx}")
    private String nginxRtmpHost;

    @Value("${livora.nginx.rtmp.port:1935}")
    private int nginxRtmpPort;

    @Value("${livora.nginx.rtmp.app:live}")
    private String nginxRtmpApp;

    /**
     * Pushes a transcode start job to {@code stream:transcode:jobs}.
     * The transcode-worker will BLPOP this key and spawn an FFmpeg process.
     *
     * @param streamId  UUID of the {@link com.joinlivora.backend.streaming.Stream}
     * @param creatorId creator user ID (Long, for logging / worker correlation)
     * @param streamKey RTMP publish key (the path segment after the app name)
     */
    public void publishStartJob(UUID streamId, Long creatorId, String streamKey) {
        String rtmpUrl = buildRtmpUrl(streamKey);
        String payload = buildStartPayload(streamId, creatorId, rtmpUrl);
        try {
            redisTemplate.opsForList().rightPush(JOBS_LIST_KEY, payload);
            log.info("TRANSCODE_JOB_PUBLISHED: streamId={} creatorId={} rtmpUrl={}", streamId, creatorId, rtmpUrl);
        } catch (Exception e) {
            // Non-fatal: WebRTC stream continues without HLS transcoding.
            // The transcode-worker exposes a REST endpoint for manual re-queue if needed.
            log.error("TRANSCODE_JOB_PUBLISH_FAILED: streamId={} creatorId={} reason={} — " +
                      "stream will be WebRTC-only until worker recovers", streamId, creatorId, e.getMessage());
        }
    }

    /**
     * Publishes a stop signal to {@code stream:transcode:stop} via Redis Pub/Sub.
     * The transcode-worker's subscriber will kill the running FFmpeg process for
     * this stream and clean up HLS segment files.
     *
     * @param streamId UUID of the stream to stop
     */
    public void publishStopSignal(UUID streamId) {
        try {
            redisTemplate.convertAndSend(STOP_CHANNEL, streamId.toString());
            log.info("TRANSCODE_STOP_PUBLISHED: streamId={}", streamId);
        } catch (Exception e) {
            log.error("TRANSCODE_STOP_PUBLISH_FAILED: streamId={} reason={} — " +
                      "FFmpeg process may need manual cleanup", streamId, e.getMessage());
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String buildRtmpUrl(String streamKey) {
        return String.format("rtmp://%s:%d/%s/%s", nginxRtmpHost, nginxRtmpPort, nginxRtmpApp, streamKey);
    }

    /**
     * Builds the JSON job payload without a heavyweight JSON library dependency
     * (StringRedisTemplate only handles String values; the worker parses via Jackson).
     */
    private String buildStartPayload(UUID streamId, Long creatorId, String rtmpUrl) {
        long queuedAt = Instant.now().toEpochMilli();
        return String.format(
            "{\"streamId\":\"%s\",\"creatorId\":%d,\"rtmpUrl\":\"%s\",\"queuedAt\":%d}",
            streamId, creatorId, rtmpUrl, queuedAt
        );
    }
}
