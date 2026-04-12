package com.joinlivora.backend.streaming.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.config.MetricsService;
import com.joinlivora.backend.streaming.StreamCacheDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed cache for the live streams explore page.
 *
 * <h3>Key schema</h3>
 * <ul>
 *   <li>{@code streams:live} — Sorted Set, score = epoch-seconds of stream start, member = streamId (UUID string)</li>
 *   <li>{@code stream:data:{streamId}} — JSON-serialized {@link StreamCacheDTO}, TTL 10 min</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>{@link #addStream} — called from {@code CreatorGoLiveService} after stream is persisted</li>
 *   <li>{@link #removeStream} — called from {@code LivestreamEventListener} on stream end</li>
 *   <li>{@link #getLiveStreamIds} — returns up to {@code limit} most-recent stream IDs</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamCacheService {

    public static final String STREAMS_LIVE_KEY   = "streams:live";
    public static final String STREAM_DATA_PREFIX  = "stream:data:";
    public static final Duration STREAM_DATA_TTL   = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    /**
     * Add a live stream to the ZSET and cache its data.
     *
     * @param dto        serializable stream metadata
     * @param startedAt  stream start instant (used as ZADD score)
     */
    public void addStream(StreamCacheDTO dto, Instant startedAt) {
        if (dto == null || dto.getId() == null) {
            return;
        }
        try {
            double score = (double) startedAt.getEpochSecond();
            String streamIdStr = dto.getId().toString();

            // ZADD streams:live <score> <streamId>
            redisTemplate.opsForZSet().add(STREAMS_LIVE_KEY, streamIdStr, score);

            // SET stream:data:{streamId} <json> EX 600
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(STREAM_DATA_PREFIX + streamIdStr, json, STREAM_DATA_TTL);

            log.info("StreamCache: added stream {} to streams:live (score={})", streamIdStr, score);
        } catch (Exception e) {
            log.warn("REDIS FALLBACK ACTIVATED: StreamCache addStream failed for {}: {}",
                    dto.getId(), e.getMessage());
        }
    }

    /**
     * Remove a stream from the ZSET and delete its cached data.
     *
     * @param streamId the UUID of the stream that ended
     */
    public void removeStream(UUID streamId) {
        if (streamId == null) {
            return;
        }
        try {
            String streamIdStr = streamId.toString();
            redisTemplate.opsForZSet().remove(STREAMS_LIVE_KEY, streamIdStr);
            redisTemplate.delete(STREAM_DATA_PREFIX + streamIdStr);
            log.info("StreamCache: removed stream {} from streams:live", streamIdStr);
        } catch (Exception e) {
            log.warn("REDIS FALLBACK ACTIVATED: StreamCache removeStream failed for {}: {}",
                    streamId, e.getMessage());
        }
    }

    /**
     * Get the most-recent live stream IDs from the ZSET.
     *
     * @param limit maximum number of stream IDs to return (e.g. 50)
     * @return ordered list (newest first) of stream UUIDs
     */
    public List<UUID> getLiveStreamIds(int limit) {
        try {
            // ZREVRANGE — highest score (most recent) first
            Set<String> members = redisTemplate.opsForZSet()
                    .reverseRange(STREAMS_LIVE_KEY, 0, limit - 1L);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            List<UUID> ids = new ArrayList<>(members.size());
            for (String m : members) {
                try {
                    ids.add(UUID.fromString(m));
                } catch (IllegalArgumentException ex) {
                    log.warn("StreamCache: invalid UUID in streams:live ZSET: '{}'", m);
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("REDIS FALLBACK ACTIVATED: StreamCache getLiveStreamIds failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch cached {@link StreamCacheDTO} for a specific stream.
     * Returns {@code null} on cache miss or Redis failure.
     *
     * @param streamId UUID of the stream
     * @return cached DTO or {@code null}
     */
    public StreamCacheDTO getStreamData(UUID streamId) {
        if (streamId == null) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(STREAM_DATA_PREFIX + streamId.toString());
            if (json != null) {
                metricsService.getCacheStreamHit().increment();
                return objectMapper.readValue(json, StreamCacheDTO.class);
            }
        } catch (Exception e) {
            log.debug("StreamCache: getStreamData failed for {}: {}", streamId, e.getMessage());
        }
        metricsService.getCacheStreamMiss().increment();
        return null;
    }

    /**
     * Check whether the ZSET contains at least one live stream entry
     * (fast existence check without reading all members).
     */
    public boolean hasLiveStreams() {
        try {
            Long count = redisTemplate.opsForZSet().zCard(STREAMS_LIVE_KEY);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
