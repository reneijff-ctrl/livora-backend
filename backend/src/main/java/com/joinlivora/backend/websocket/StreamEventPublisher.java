package com.joinlivora.backend.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Publishes stream-related events through a single composite WebSocket topic
 * per creator, reducing the number of subscriptions each viewer needs.
 *
 * <p>All stream events (chat, tips, goals, leaderboard, viewer updates,
 * stream status) are sent via one unified topic:
 * {@code /exchange/amq.topic/stream.events.{creatorId}}</p>
 *
 * <p>Backward compatibility is maintained via dual-send: each event is
 * also broadcast to the original legacy topic. Callers can disable
 * dual-send per call when legacy topics are no longer needed.</p>
 *
 * <p>Example composite payload:</p>
 * <pre>{@code
 * {
 *   "type": "STREAM_EVENT",
 *   "channel": "tip",
 *   "data": { ... }
 * }
 * }</pre>
 */
@Service
@Slf4j
public class StreamEventPublisher {

    /** Well-known channel names for stream events. */
    public static final String CHANNEL_CHAT = "chat";
    public static final String CHANNEL_TIP = "tip";
    public static final String CHANNEL_GOAL = "goal";
    public static final String CHANNEL_LEADERBOARD = "leaderboard";
    public static final String CHANNEL_VIEWERS = "viewers";
    public static final String CHANNEL_STREAM_STATUS = "stream_status";

    private static final String COMPOSITE_TOPIC_PREFIX = "/exchange/amq.topic/stream.events.";

    private final SimpMessagingTemplate messagingTemplate;

    public StreamEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // ── Typed publish methods ───────────────────────────────────────────

    /**
     * Publish a chat event to both the composite topic and the legacy
     * {@code chat.{creatorId}} topic.
     *
     * @param creatorUserId the creator whose stream receives the event
     * @param data          the chat event payload
     */
    public void publishChatEvent(Long creatorUserId, Object data) {
        publish(creatorUserId, CHANNEL_CHAT, data,
                "/exchange/amq.topic/chat." + creatorUserId);
    }

    /**
     * Publish a tip/monetization event to both the composite topic and the
     * legacy {@code monetization.{creatorId}} topic.
     *
     * @param creatorUserId the creator whose stream receives the event
     * @param data          the tip event payload
     */
    public void publishTipEvent(Long creatorUserId, Object data) {
        publish(creatorUserId, CHANNEL_TIP, data,
                "/exchange/amq.topic/monetization." + creatorUserId);
    }

    /**
     * Publish a goal event to both the composite topic and the legacy
     * {@code goals.{creatorId}} topic.
     *
     * @param creatorUserId the creator whose stream receives the event
     * @param data          the goal event payload
     */
    public void publishGoalEvent(Long creatorUserId, Object data) {
        publish(creatorUserId, CHANNEL_GOAL, data,
                "/exchange/amq.topic/goals." + creatorUserId);
    }

    /**
     * Publish a leaderboard event to both the composite topic and the legacy
     * {@code leaderboard.{creatorId}} topic.
     *
     * @param creatorUserId the creator whose stream receives the event
     * @param data          the leaderboard event payload
     */
    public void publishLeaderboardEvent(Long creatorUserId, Object data) {
        publish(creatorUserId, CHANNEL_LEADERBOARD, data,
                "/exchange/amq.topic/leaderboard." + creatorUserId);
    }

    /**
     * Publish a viewer count/list update to both the composite topic and the
     * legacy {@code viewers.{creatorId}} topic.
     *
     * @param creatorUserId the creator whose stream receives the event
     * @param data          the viewer update payload
     */
    public void publishViewerEvent(Long creatorUserId, Object data) {
        publish(creatorUserId, CHANNEL_VIEWERS, data,
                "/exchange/amq.topic/viewers." + creatorUserId);
    }

    /**
     * Publish a stream status event to both the composite topic and the
     * legacy {@code stream.v2.creator.{creatorId}.status} topic.
     *
     * @param creatorUserId the creator whose stream receives the event
     * @param data          the stream status payload
     */
    public void publishStreamStatusEvent(Long creatorUserId, Object data) {
        publish(creatorUserId, CHANNEL_STREAM_STATUS, data,
                "/exchange/amq.topic/stream.v2.creator." + creatorUserId + ".status");
    }

    // ── Generic publish methods ─────────────────────────────────────────

    /**
     * Publish a single event on a custom channel with dual-send to a legacy topic.
     *
     * @param creatorUserId  the creator whose stream receives the event
     * @param channel        the logical channel name (e.g. "chat", "tip")
     * @param data           the event payload
     * @param legacyTopic    the original topic to dual-send to (null to skip)
     */
    public void publish(Long creatorUserId, String channel, Object data,
                        String legacyTopic) {
        if (creatorUserId == null || data == null) {
            return;
        }

        StreamEvent event = StreamEvent.builder()
                .type("STREAM_EVENT")
                .channel(channel)
                .data(data)
                .build();

        String compositeTopic = COMPOSITE_TOPIC_PREFIX + creatorUserId;

        try {
            messagingTemplate.convertAndSend(compositeTopic, event);
        } catch (Exception e) {
            log.error("Failed to publish composite {} event for creator {}: {}",
                    channel, creatorUserId, e.getMessage());
        }

        // Dual-send to legacy topic for backward compatibility
        if (legacyTopic != null) {
            try {
                messagingTemplate.convertAndSend(legacyTopic, data);
            } catch (Exception e) {
                log.error("Failed to dual-send {} to legacy topic for creator {}: {}",
                        channel, creatorUserId, e.getMessage());
            }
        }
    }

    /**
     * Publish multiple events as a single composite batch on the unified topic.
     * Also dual-sends each event individually to its legacy topic.
     *
     * @param creatorUserId the creator whose stream receives the events
     * @param events        list of channel+data pairs to batch together
     */
    public void publishCompositeEvent(Long creatorUserId,
                                      List<CompositeEntry> events) {
        if (creatorUserId == null || events == null || events.isEmpty()) {
            return;
        }

        List<StreamEvent> streamEvents = new ArrayList<>(events.size());
        for (CompositeEntry entry : events) {
            streamEvents.add(StreamEvent.builder()
                    .type("STREAM_EVENT")
                    .channel(entry.getChannel())
                    .data(entry.getData())
                    .build());
        }

        Map<String, Object> batch = Map.of(
                "type", "STREAM_EVENT_BATCH",
                "events", streamEvents
        );

        String compositeTopic = COMPOSITE_TOPIC_PREFIX + creatorUserId;

        try {
            messagingTemplate.convertAndSend(compositeTopic, batch);
        } catch (Exception e) {
            log.error("Failed to publish composite batch for creator {}: {}",
                    creatorUserId, e.getMessage());
        }

        // Dual-send each event to its legacy topic
        for (CompositeEntry entry : events) {
            if (entry.getLegacyTopic() != null && entry.getData() != null) {
                try {
                    messagingTemplate.convertAndSend(entry.getLegacyTopic(), entry.getData());
                } catch (Exception e) {
                    log.error("Failed to dual-send {} to legacy topic for creator {}: {}",
                            entry.getChannel(), creatorUserId, e.getMessage());
                }
            }
        }
    }

    // ── Inner DTOs ──────────────────────────────────────────────────────

    /**
     * A single event on the composite stream topic.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StreamEvent {
        private String type;
        private String channel;
        private Object data;
    }

    /**
     * Input for {@link #publishCompositeEvent}: groups a channel, its payload,
     * and (optionally) the legacy topic for dual-send.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompositeEntry {
        private String channel;
        private Object data;
        private String legacyTopic;
    }
}
