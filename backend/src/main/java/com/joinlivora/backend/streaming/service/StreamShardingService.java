package com.joinlivora.backend.streaming.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Distributes per-stream Redis keys across N logical shards to eliminate hot-key bottlenecks
 * on viral streams.
 *
 * <p>Without sharding, a stream with 1M viewers concentrates all Redis writes on a single key
 * (e.g. {@code stream:{streamId}:viewers}). This key becomes a Redis hot slot, causing ~50-100k
 * IOPS on a single shard node and saturating the network connection to that slot.
 *
 * <p>With sharding, each viewer is deterministically assigned to one of {@code shardCount} logical
 * partitions. Per-viewer operations (join, leave, count) target
 * {@code stream:{streamId}:shard:{N}:viewers} instead of the global key. Aggregation (total
 * viewer count) is done lazily by summing shard counts — a background job refreshes the
 * aggregated total every 5 seconds into {@code stream:{streamId}:viewer-count:total}.
 *
 * <p>Shard assignment uses {@code viewerUserId mod shardCount} so a returning viewer always lands
 * on the same shard, making leave/remove O(1) without a global scan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamShardingService {

    private final StringRedisTemplate redisTemplate;

    /** Number of logical shards per stream. Must be a power of 2 for even distribution. */
    @Value("${livora.streaming.shard-count:16}")
    private int shardCount;

    // ── Key templates ────────────────────────────────────────────────────────────────────────

    private static final String SHARD_VIEWERS_KEY     = "stream:%s:shard:%d:viewers";
    private static final String SHARD_CHAT_KEY        = "stream:%s:shard:%d:chat";
    private static final String SHARD_PRESENCE_KEY    = "stream:%s:shard:%d:presence";
    private static final String TOTAL_VIEWER_CACHE    = "stream:%s:viewer-count:total";
    private static final String VIEWER_SHARD_INDEX    = "stream:%s:viewer-shard:%d";  // which shard a viewer is on

    // ── Shard assignment ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the shard index [0, shardCount) for the given viewer.
     * Consistent: same userId → same shard always.
     */
    public int getViewerShard(Long viewerUserId) {
        return (int) (Math.abs(viewerUserId) % shardCount);
    }

    /**
     * Returns the shard index for chat message routing.
     * Using a random-ish assignment based on message content hash for even distribution.
     */
    public int getChatShard(String messageKey) {
        return Math.abs(messageKey.hashCode()) % shardCount;
    }

    /**
     * Returns the sharded viewer-set key for this stream + viewer combination.
     */
    public String viewerShardKey(UUID streamId, Long viewerUserId) {
        int shard = getViewerShard(viewerUserId);
        return String.format(SHARD_VIEWERS_KEY, streamId, shard);
    }

    /**
     * Returns the sharded chat channel key for this stream + shard.
     */
    public String chatShardKey(UUID streamId, int shardIndex) {
        return String.format(SHARD_CHAT_KEY, streamId, shardIndex);
    }

    /**
     * Returns the sharded presence key for this stream + shard.
     */
    public String presenceShardKey(UUID streamId, int shardIndex) {
        return String.format(SHARD_PRESENCE_KEY, streamId, shardIndex);
    }

    // ── Viewer tracking ───────────────────────────────────────────────────────────────────────

    /**
     * Records a viewer join on their deterministic shard.
     */
    public void addViewer(UUID streamId, Long viewerUserId) {
        try {
            String key = viewerShardKey(streamId, viewerUserId);
            redisTemplate.opsForSet().add(key, viewerUserId.toString());
            invalidateTotalCache(streamId);
            log.debug("SHARD JOIN stream={} viewer={} shard={}", streamId, viewerUserId,
                    getViewerShard(viewerUserId));
        } catch (Exception e) {
            log.warn("SHARD ADD FAILED stream={} viewer={}: {}", streamId, viewerUserId,
                    e.getMessage());
        }
    }

    /**
     * Records a viewer leave on their deterministic shard.
     */
    public void removeViewer(UUID streamId, Long viewerUserId) {
        try {
            String key = viewerShardKey(streamId, viewerUserId);
            redisTemplate.opsForSet().remove(key, viewerUserId.toString());
            invalidateTotalCache(streamId);
        } catch (Exception e) {
            log.warn("SHARD REMOVE FAILED stream={} viewer={}: {}", streamId, viewerUserId,
                    e.getMessage());
        }
    }

    /**
     * Returns the live viewer count by summing all shard set cardinalities.
     * Falls back to the cached total if Redis is degraded.
     */
    public long getViewerCount(UUID streamId) {
        // Try fast path: cached total
        try {
            String cached = redisTemplate.opsForValue().get(
                    String.format(TOTAL_VIEWER_CACHE, streamId));
            if (cached != null) {
                return Long.parseLong(cached);
            }
        } catch (Exception e) {
            log.debug("SHARD TOTAL CACHE MISS stream={}: {}", streamId, e.getMessage());
        }

        // Slow path: sum all shards
        long total = 0;
        for (int i = 0; i < shardCount; i++) {
            try {
                String key = String.format(SHARD_VIEWERS_KEY, streamId, i);
                Long size = redisTemplate.opsForSet().size(key);
                if (size != null) {
                    total += size;
                }
            } catch (Exception e) {
                log.warn("SHARD COUNT FAILED stream={} shard={}: {}", streamId, i, e.getMessage());
            }
        }
        return total;
    }

    /**
     * Refreshes the cached total viewer count for a stream.
     * Called by a scheduled aggregator every 5 seconds.
     */
    public void refreshTotalViewerCount(UUID streamId) {
        long total = 0;
        for (int i = 0; i < shardCount; i++) {
            try {
                String key = String.format(SHARD_VIEWERS_KEY, streamId, i);
                Long size = redisTemplate.opsForSet().size(key);
                if (size != null) {
                    total += size;
                }
            } catch (Exception ignored) { }
        }
        try {
            redisTemplate.opsForValue().set(
                    String.format(TOTAL_VIEWER_CACHE, streamId), String.valueOf(total));
            log.debug("SHARD TOTAL REFRESHED stream={} total={}", streamId, total);
        } catch (Exception e) {
            log.warn("SHARD TOTAL CACHE WRITE FAILED stream={}: {}", streamId, e.getMessage());
        }
    }

    /**
     * Cleans up all shard keys for a stream on stream end.
     */
    public void cleanupStreamShards(UUID streamId) {
        for (int i = 0; i < shardCount; i++) {
            try {
                redisTemplate.delete(String.format(SHARD_VIEWERS_KEY, streamId, i));
                redisTemplate.delete(String.format(SHARD_CHAT_KEY, streamId, i));
                redisTemplate.delete(String.format(SHARD_PRESENCE_KEY, streamId, i));
            } catch (Exception e) {
                log.warn("SHARD CLEANUP FAILED stream={} shard={}: {}", streamId, i,
                        e.getMessage());
            }
        }
        try {
            redisTemplate.delete(String.format(TOTAL_VIEWER_CACHE, streamId));
        } catch (Exception ignored) { }
        log.info("SHARD CLEANUP COMPLETE stream={} shards={}", streamId, shardCount);
    }

    // ── Internals ─────────────────────────────────────────────────────────────────────────────

    private void invalidateTotalCache(UUID streamId) {
        try {
            redisTemplate.delete(String.format(TOTAL_VIEWER_CACHE, streamId));
        } catch (Exception ignored) { }
    }

    public int getShardCount() {
        return shardCount;
    }
}
