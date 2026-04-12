package com.joinlivora.backend.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.config.MetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Redis Pub/Sub service for chat message fan-out.
 *
 * <p>When {@code livora.messaging.redis-pubsub-enabled=true}, messages are published to
 * {@code chat:room:{creatorId}} Redis channels in addition to the RabbitMQ STOMP topic.
 * Each pod subscribes to the {@code chat:room:*} pattern and forwards received messages
 * to local WebSocket subscribers via {@code SimpMessagingTemplate}, replacing the
 * RabbitMQ → N-queue fan-out with a direct, cluster-safe broadcast.
 *
 * <p>RabbitMQ publishing is kept as a parallel path during the migration period so that
 * any subscribers still on the old STOMP topic continue to receive messages.
 */
@Service
@Slf4j
public class RedisPubSubChatService implements MessageListener {

    /** Redis Pub/Sub channel prefix for chat rooms. */
    public static final String CHAT_CHANNEL_PREFIX = "chat:room:";

    /** Pattern used to subscribe to all chat room channels. */
    private static final String CHAT_CHANNEL_PATTERN = CHAT_CHANNEL_PREFIX + "*";

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final RedisMessageListenerContainer listenerContainer;

    @Value("${livora.messaging.redis-pubsub-enabled:false}")
    private boolean pubSubEnabled;

    public RedisPubSubChatService(StringRedisTemplate redisTemplate,
                                  SimpMessagingTemplate messagingTemplate,
                                  ObjectMapper objectMapper,
                                  MetricsService metricsService,
                                  RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.listenerContainer = listenerContainer;

        // Subscribe to all chat:room:* channels on startup
        this.listenerContainer.addMessageListener(this, new PatternTopic(CHAT_CHANNEL_PATTERN));
        log.info("RedisPubSubChatService: subscribed to pattern '{}'", CHAT_CHANNEL_PATTERN);
    }

    /**
     * Publish a single chat message to the Redis Pub/Sub channel for the creator.
     * No-op (and fail-open) when the feature flag is disabled or Redis is unavailable.
     *
     * @param creatorUserId creator whose room the message belongs to
     * @param message       the chat message DTO
     */
    public void publish(Long creatorUserId, ChatMessageDto message) {
        if (!pubSubEnabled) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(message);
            String channel = CHAT_CHANNEL_PREFIX + creatorUserId;
            redisTemplate.convertAndSend(channel, json);
            metricsService.getChatMessagesPubSubSent().increment();
            log.debug("PubSub: published chat message to channel '{}'", channel);
        } catch (JsonProcessingException e) {
            log.warn("PubSub: failed to serialize message for creator {}: {}", creatorUserId, e.getMessage());
        } catch (Exception e) {
            log.warn("REDIS FALLBACK ACTIVATED: PubSub publish failed for creator {}, RabbitMQ will carry: {}",
                    creatorUserId, e.getMessage());
        }
    }

    /**
     * Publish a batch payload (Map) to the Redis Pub/Sub channel for the creator.
     * Used by {@link RedisChatBatchService} when flushing multiple messages at once.
     *
     * @param creatorUserId creator whose room the batch belongs to
     * @param batchPayload  {@code Map<String, Object>} with {@code type} and {@code messages} keys
     */
    public void publishBatch(Long creatorUserId, Map<String, Object> batchPayload) {
        if (!pubSubEnabled) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(batchPayload);
            String channel = CHAT_CHANNEL_PREFIX + creatorUserId;
            redisTemplate.convertAndSend(channel, json);
            int size = batchPayload.containsKey("messages")
                    ? ((java.util.List<?>) batchPayload.get("messages")).size()
                    : 1;
            metricsService.getChatMessagesPubSubSent().increment(size);
            log.debug("PubSub: published chat batch ({} msgs) to channel '{}'", size, channel);
        } catch (Exception e) {
            log.warn("REDIS FALLBACK ACTIVATED: PubSub batch publish failed for creator {}, RabbitMQ will carry: {}",
                    creatorUserId, e.getMessage());
        }
    }

    /**
     * MessageListener callback — invoked on every Redis Pub/Sub message matching
     * the {@code chat:room:*} pattern on this pod.
     *
     * <p>Deserializes the JSON payload and forwards it to the local STOMP topic
     * {@code /topic/chat.{creatorId}} so connected WebSocket clients receive it instantly
     * without a round-trip through RabbitMQ.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (!pubSubEnabled) {
            return;
        }
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        // Extract creatorId from channel name "chat:room:{creatorId}"
        String creatorIdStr = channel.substring(CHAT_CHANNEL_PREFIX.length());
        String stompTopic = "/topic/chat." + creatorIdStr;

        try {
            // Attempt to deserialize as single ChatMessageDto
            ChatMessageDto dto = objectMapper.readValue(body, ChatMessageDto.class);
            messagingTemplate.convertAndSend(stompTopic, dto);
            metricsService.getChatMessagesPubSubReceived().increment();
            log.debug("PubSub: forwarded chat message to STOMP topic '{}'", stompTopic);
        } catch (Exception e) {
            // Fallback: try to forward as raw Map (batch payload or unknown shape)
            try {
                Object payload = objectMapper.readValue(body, Object.class);
                messagingTemplate.convertAndSend(stompTopic, payload);
                metricsService.getChatMessagesPubSubReceived().increment();
                log.debug("PubSub: forwarded raw payload to STOMP topic '{}'", stompTopic);
            } catch (Exception ex) {
                log.warn("PubSub: failed to forward message on channel '{}': {}", channel, ex.getMessage());
            }
        }
    }

    public boolean isPubSubEnabled() {
        return pubSubEnabled;
    }
}
