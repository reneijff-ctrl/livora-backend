package com.joinlivora.backend.chat;

import com.joinlivora.backend.streaming.service.StreamShardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Routes chat messages across partitioned Redis channels to scale past RabbitMQ single-node limits.
 *
 * <h3>Architecture</h3>
 * <p>Standard RabbitMQ fan-out saturates at ~50k–100k msg/s on a single broker. At 1M viewers
 * in a viral stream each sending 1 msg every 10s, that is 100k msg/s from chat alone — 2× the
 * single-broker ceiling before any other traffic.
 *
 * <p>This service shards chat messages across {@code partitionCount} Redis Pub/Sub channels:
 * <pre>
 *   chat:{streamId}:part:{0..N-1}
 * </pre>
 * Each WebSocket server pod subscribes to ALL partitions for a stream but delivers only to its
 * own locally connected viewers. Since viewers are spread across pods by the load balancer, each
 * pod handles roughly {@code 1/podCount} of all viewers — the fan-out multiplier is bounded.
 *
 * <h3>Viewer subscription assignment</h3>
 * <p>Viewers subscribe to their assigned partition based on {@code viewerUserId % partitionCount}.
 * This means:
 * <ul>
 *   <li>All messages on partition 3 go to all viewers whose userId % 16 == 3</li>
 *   <li>A viewer only receives messages from their partition — chat is sampled, not full</li>
 *   <li>Moderator/creator messages are broadcast to ALL partitions (fan-out to all viewers)</li>
 * </ul>
 *
 * <h3>Fallback</h3>
 * <p>If Redis Pub/Sub is unavailable, falls back to direct STOMP send, which will hit the
 * RabbitMQ path. This preserves functionality at reduced scale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartitionedChatService {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final StreamShardingService shardingService;

    /** Number of chat partitions per stream. 16 partitions × 50k msg/s/broker = 800k msg/s */
    @Value("${livora.chat.partition-count:16}")
    private int partitionCount;

    /** Whether partitioned chat is active. Off by default for gradual rollout. */
    @Value("${livora.chat.partitioned-enabled:false}")
    private boolean partitionedEnabled;

    // ── Key patterns ─────────────────────────────────────────────────────────────────────────

    /** Redis Pub/Sub channel for a specific partition of a stream's chat. */
    private static final String PARTITION_CHANNEL = "chat:%s:part:%d";

    /** STOMP topic for a viewer's assigned chat partition. */
    private static final String STOMP_PARTITION_TOPIC = "/topic/chat.%s.part.%d";

    /** STOMP topic for broadcast messages (creator/moderator) — all partitions. */
    private static final String STOMP_BROADCAST_TOPIC = "/topic/chat.%s.broadcast";

    // ── Routing ───────────────────────────────────────────────────────────────────────────────

    /**
     * Returns the partition index for the given viewer.
     * Consistent: same userId → same partition always.
     */
    public int getViewerPartition(Long viewerUserId) {
        return (int) (Math.abs(viewerUserId) % partitionCount);
    }

    /**
     * Returns the Redis channel name for a specific partition.
     */
    public String getPartitionChannel(UUID streamId, int partition) {
        return String.format(PARTITION_CHANNEL, streamId, partition);
    }

    /**
     * Returns the STOMP topic a viewer should subscribe to for their chat partition.
     */
    public String getViewerStompTopic(UUID streamId, Long viewerUserId) {
        int partition = getViewerPartition(viewerUserId);
        return String.format(STOMP_PARTITION_TOPIC, streamId, partition);
    }

    // ── Publishing ────────────────────────────────────────────────────────────────────────────

    /**
     * Publishes a regular chat message to the viewer's assigned partition.
     * All other viewers on different partitions will NOT receive this message —
     * this is intentional for scale (at 1M viewers, sampled chat is required).
     *
     * @param streamId  the UUID of the live stream
     * @param senderId  the userId of the message sender
     * @param payload   the serialized message payload (JSON)
     */
    public void publishChatMessage(UUID streamId, Long senderId, String payload) {
        if (!partitionedEnabled) {
            // Fallback: standard STOMP broadcast to all viewers via RabbitMQ
            messagingTemplate.convertAndSend(
                    String.format("/exchange/amq.topic/chat.%s", streamId), payload);
            return;
        }

        int partition = getViewerPartition(senderId);
        String channel = getPartitionChannel(streamId, partition);
        try {
            redisTemplate.convertAndSend(channel, payload);
            log.debug("PARTITIONED CHAT stream={} sender={} partition={}", streamId, senderId,
                    partition);
        } catch (Exception e) {
            log.warn("PARTITIONED CHAT FAILED stream={} partition={}: {} — falling back to STOMP",
                    streamId, partition, e.getMessage());
            // Fail-open: fall back to the standard STOMP topic
            messagingTemplate.convertAndSend(
                    String.format("/exchange/amq.topic/chat.%s", streamId), payload);
        }
    }

    /**
     * Broadcasts a high-priority message to ALL partitions of a stream's chat.
     * Used for: creator messages, moderator announcements, system events, tips.
     * These bypass the partition filter so every viewer receives them.
     *
     * @param streamId the UUID of the live stream
     * @param payload  the serialized message payload (JSON)
     */
    public void broadcastToAllPartitions(UUID streamId, String payload) {
        if (!partitionedEnabled) {
            messagingTemplate.convertAndSend(
                    String.format("/exchange/amq.topic/chat.%s", streamId), payload);
            return;
        }

        int successCount = 0;
        for (int i = 0; i < partitionCount; i++) {
            try {
                redisTemplate.convertAndSend(getPartitionChannel(streamId, i), payload);
                successCount++;
            } catch (Exception e) {
                log.warn("BROADCAST PARTITION FAILED stream={} part={}: {}",
                        streamId, i, e.getMessage());
            }
        }
        if (successCount < partitionCount) {
            // At least one partition failed — also send via STOMP as safety net
            messagingTemplate.convertAndSend(
                    String.format("/exchange/amq.topic/chat.%s", streamId), payload);
        }
        log.debug("BROADCAST CHAT stream={} partitions={}/{}", streamId, successCount,
                partitionCount);
    }

    // ── Metrics ───────────────────────────────────────────────────────────────────────────────

    public int getPartitionCount() {
        return partitionCount;
    }

    public boolean isPartitionedEnabled() {
        return partitionedEnabled;
    }
}
