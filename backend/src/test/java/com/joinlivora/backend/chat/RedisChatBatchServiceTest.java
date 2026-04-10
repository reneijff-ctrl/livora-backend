package com.joinlivora.backend.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.config.MetricsService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class RedisChatBatchServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private LiveViewerCounterService liveViewerCounterService;

    @Mock
    private ListOperations<String, String> listOps;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private MetricsService metricsService;

    @Mock
    private Counter mockCounter;

    @Mock
    private RedisPubSubChatService pubSubChatService;

    private ObjectMapper objectMapper;
    private RedisChatBatchService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        lenient().when(metricsService.getChatMessagesSent()).thenReturn(mockCounter);
        lenient().when(metricsService.getChatMessagesRetried()).thenReturn(mockCounter);
        lenient().when(metricsService.getChatMessagesFailed()).thenReturn(mockCounter);

        lenient().when(metricsService.getChatMessagesPubSubSent()).thenReturn(mockCounter);
        lenient().when(metricsService.getChatMessagesPubSubReceived()).thenReturn(mockCounter);

        com.joinlivora.backend.resilience.RedisCircuitBreakerService redisCb =
                new com.joinlivora.backend.resilience.RedisCircuitBreakerService(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        service = new RedisChatBatchService(
                redisTemplate, messagingTemplate, liveViewerCounterService, objectMapper, metricsService,
                pubSubChatService, redisCb);
    }

    // --- enqueueMessage tests ---

    @Test
    void enqueueMessage_SmallRoom_ShouldBroadcastImmediately() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(30L);

        ChatMessageDto msg = buildMessage("Hello");
        service.enqueueMessage(1L, msg);

        verify(messagingTemplate).convertAndSend("/exchange/amq.topic/chat.1", msg);
        verifyNoInteractions(listOps);
    }

    @Test
    void enqueueMessage_LargeRoom_ShouldBufferInRedis() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(100L);

        ChatMessageDto msg = buildMessage("Hello");
        service.enqueueMessage(1L, msg);

        verify(listOps).rightPush(eq("chat:batch:1"), anyString());
        verify(redisTemplate).expire("chat:batch:1", Duration.ofSeconds(5));
        verify(setOps).add("chat:batch:active", "1");
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void enqueueMessage_AtThresholdBoundary_ShouldBuffer() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(50L);

        ChatMessageDto msg = buildMessage("At boundary");
        service.enqueueMessage(1L, msg);

        verify(listOps).rightPush(eq("chat:batch:1"), anyString());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void enqueueMessage_BelowThreshold_ShouldBroadcastImmediately() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(49L);

        ChatMessageDto msg = buildMessage("Below threshold");
        service.enqueueMessage(1L, msg);

        verify(messagingTemplate).convertAndSend("/exchange/amq.topic/chat.1", msg);
        verifyNoInteractions(listOps);
    }

    @Test
    void enqueueMessage_NullCreatorId_ShouldDoNothing() {
        service.enqueueMessage(null, buildMessage("test"));

        verifyNoInteractions(messagingTemplate, liveViewerCounterService, listOps);
    }

    @Test
    void enqueueMessage_NullMessage_ShouldDoNothing() {
        service.enqueueMessage(1L, null);

        verifyNoInteractions(messagingTemplate, liveViewerCounterService, listOps);
    }

    @Test
    void enqueueMessage_RedisError_ShouldFallbackToDirectSend() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(100L);
        when(listOps.rightPush(anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        ChatMessageDto msg = buildMessage("Fallback");
        service.enqueueMessage(1L, msg);

        verify(messagingTemplate).convertAndSend("/exchange/amq.topic/chat.1", msg);
    }

    // --- flushBatches tests ---

    @Test
    void flushBatches_EmptyActiveSet_ShouldNotSend() {
        when(setOps.members("chat:batch:active")).thenReturn(Collections.emptySet());

        service.flushBatches();

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void flushBatches_NullActiveSet_ShouldNotSend() {
        when(setOps.members("chat:batch:active")).thenReturn(null);

        service.flushBatches();

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void flushBatches_SingleMessage_ShouldSendAsIs() throws JsonProcessingException {
        ChatMessageDto msg = buildMessage("Hello");
        String json = objectMapper.writeValueAsString(msg);

        when(setOps.members("chat:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("chat:batch:lock:1", "1", Duration.ofMillis(200)))
                .thenReturn(true);
        when(listOps.range("chat:batch:1", 0, -1)).thenReturn(List.of(json));

        service.flushBatches();

        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.1"), captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Hello");
        // Delete-on-success: trim the 1 processed message
        verify(listOps).trim("chat:batch:1", 1, -1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void flushBatches_MultipleMessages_ShouldSendBatch() throws JsonProcessingException {
        ChatMessageDto msg1 = buildMessage("Hello");
        ChatMessageDto msg2 = buildMessage("World");
        ChatMessageDto msg3 = buildMessage("!");
        String json1 = objectMapper.writeValueAsString(msg1);
        String json2 = objectMapper.writeValueAsString(msg2);
        String json3 = objectMapper.writeValueAsString(msg3);

        when(setOps.members("chat:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("chat:batch:lock:1", "1", Duration.ofMillis(200)))
                .thenReturn(true);
        when(listOps.range("chat:batch:1", 0, -1)).thenReturn(List.of(json1, json2, json3));

        service.flushBatches();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.1"), captor.capture());

        Map<String, Object> batch = captor.getValue();
        assertThat(batch.get("type")).isEqualTo("CHAT_BATCH");
        assertThat((List<?>) batch.get("messages")).hasSize(3);
        // Delete-on-success: trim the 3 processed messages
        verify(listOps).trim("chat:batch:1", 3, -1);
    }

    @Test
    void flushBatches_LockNotAcquired_ShouldSkipCreator() {
        when(setOps.members("chat:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("chat:batch:lock:1", "1", Duration.ofMillis(200)))
                .thenReturn(false);

        service.flushBatches();

        verifyNoInteractions(messagingTemplate);
        verify(listOps, never()).range(anyString(), anyLong(), anyLong());
    }

    @Test
    void flushBatches_EmptyBatchKey_ShouldNotSend() {
        when(setOps.members("chat:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("chat:batch:lock:1", "1", Duration.ofMillis(200)))
                .thenReturn(true);
        when(listOps.range("chat:batch:1", 0, -1)).thenReturn(Collections.emptyList());

        service.flushBatches();

        verifyNoInteractions(messagingTemplate);
        // Nothing to trim when empty
        verify(listOps, never()).trim(anyString(), anyLong(), anyLong());
    }

    @Test
    void flushBatches_MultipleCreators_ShouldFlushSeparately() throws JsonProcessingException {
        ChatMessageDto msg1 = buildMessage("Room1");
        ChatMessageDto msg2 = buildMessage("Room2");
        String json1 = objectMapper.writeValueAsString(msg1);
        String json2 = objectMapper.writeValueAsString(msg2);

        when(setOps.members("chat:batch:active")).thenReturn(Set.of("1", "2"));
        when(valueOps.setIfAbsent(eq("chat:batch:lock:1"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(valueOps.setIfAbsent(eq("chat:batch:lock:2"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(listOps.range("chat:batch:1", 0, -1)).thenReturn(List.of(json1));
        when(listOps.range("chat:batch:2", 0, -1)).thenReturn(List.of(json2));

        service.flushBatches();

        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.1"), any(ChatMessageDto.class));
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.2"), any(ChatMessageDto.class));
        verify(listOps).trim("chat:batch:1", 1, -1);
        verify(listOps).trim("chat:batch:2", 1, -1);
    }

    @Test
    void flushBatches_InvalidCreatorId_ShouldRemoveFromActiveSet() {
        when(setOps.members("chat:batch:active")).thenReturn(Set.of("not-a-number"));

        service.flushBatches();

        verify(setOps).remove("chat:batch:active", "not-a-number");
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void flushBatches_RedisReadError_ShouldNotCrash() {
        when(setOps.members("chat:batch:active"))
                .thenThrow(new RuntimeException("Redis down"));

        service.flushBatches();

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void flushBatches_CorruptedJson_ShouldSkipBadMessages() throws JsonProcessingException {
        ChatMessageDto validMsg = buildMessage("Valid");
        String validJson = objectMapper.writeValueAsString(validMsg);

        when(setOps.members("chat:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("chat:batch:lock:1", "1", Duration.ofMillis(200)))
                .thenReturn(true);
        when(listOps.range("chat:batch:1", 0, -1)).thenReturn(List.of("{invalid json}", validJson));

        service.flushBatches();

        // Only the valid message should be sent
        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.1"), captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Valid");
        // Delete-on-success: trim the 2 peeked items (1 bad + 1 good)
        verify(listOps).trim("chat:batch:1", 2, -1);
    }

    @Test
    void flushBatches_AllCorruptedJson_ShouldNotSend() {
        when(setOps.members("chat:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("chat:batch:lock:1", "1", Duration.ofMillis(200)))
                .thenReturn(true);
        when(listOps.range("chat:batch:1", 0, -1)).thenReturn(List.of("{bad1}", "{bad2}"));

        service.flushBatches();

        verifyNoInteractions(messagingTemplate);
        // Corrupted messages trimmed to avoid infinite retry loop
        verify(listOps).trim("chat:batch:1", 2, -1);
    }

    @Test
    void flushBatches_BrokerFailure_ShouldNotTrim_MessagesStayForRetry() throws JsonProcessingException {
        ChatMessageDto msg = buildMessage("Important");
        String json = objectMapper.writeValueAsString(msg);

        when(setOps.members("chat:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("chat:batch:lock:1", "1", Duration.ofMillis(200)))
                .thenReturn(true);
        when(listOps.range("chat:batch:1", 0, -1)).thenReturn(List.of(json));

        // Simulate RabbitMQ failure
        doThrow(new RuntimeException("Broker unavailable"))
                .when(messagingTemplate).convertAndSend(anyString(), any(ChatMessageDto.class));

        // Simulate Redis Pub/Sub also failing (both paths fail → no trim)
        // When both transports fail, sendViaRedisPubSubOnly re-throws, causing the outer
        // catch to log + return without trimming — messages stay in Redis for the next cycle.
        doThrow(new RuntimeException("PubSub unavailable"))
                .when(pubSubChatService).publish(anyLong(), any(ChatMessageDto.class));

        service.flushBatches();

        // Messages must NOT be trimmed — they stay in Redis for the next retry cycle
        verify(listOps, never()).trim(anyString(), anyLong(), anyLong());
    }

    // --- clearBuffer tests ---

    @Test
    void clearBuffer_ShouldDeleteKeyAndRemoveFromActiveSet() {
        when(redisTemplate.delete("chat:batch:1")).thenReturn(true);

        service.clearBuffer(1L);

        verify(redisTemplate).delete("chat:batch:1");
        verify(setOps).remove("chat:batch:active", "1");
    }

    @Test
    void clearBuffer_NullCreatorId_ShouldDoNothing() {
        service.clearBuffer(null);

        verify(redisTemplate, never()).delete(anyString());
    }

    // --- Helper ---

    private ChatMessageDto buildMessage(String content) {
        return ChatMessageDto.builder()
                .id(java.util.UUID.randomUUID().toString())
                .content(content)
                .message(content)
                .type("CHAT")
                .senderId(42L)
                .senderUsername("testuser")
                .senderRole("USER")
                .timestamp(Instant.now())
                .build();
    }
}
