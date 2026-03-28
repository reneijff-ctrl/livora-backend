package com.joinlivora.backend.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
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
import org.springframework.data.redis.core.script.RedisScript;
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

    private ObjectMapper objectMapper;
    private RedisChatBatchService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service = new RedisChatBatchService(
                redisTemplate, messagingTemplate, liveViewerCounterService, objectMapper);
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
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("chat:batch:1"))))
                .thenReturn(List.of(json));

        service.flushBatches();

        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.1"), captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Hello");
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
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("chat:batch:1"))))
                .thenReturn(List.of(json1, json2, json3));

        service.flushBatches();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.1"), captor.capture());

        Map<String, Object> batch = captor.getValue();
        assertThat(batch.get("type")).isEqualTo("CHAT_BATCH");
        assertThat((List<?>) batch.get("messages")).hasSize(3);
    }

    @Test
    void flushBatches_LockNotAcquired_ShouldSkipCreator() {
        when(setOps.members("chat:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("chat:batch:lock:1", "1", Duration.ofMillis(200)))
                .thenReturn(false);

        service.flushBatches();

        verifyNoInteractions(messagingTemplate);
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList());
    }

    @Test
    void flushBatches_EmptyBatchKey_ShouldNotSend() {
        when(setOps.members("chat:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("chat:batch:lock:1", "1", Duration.ofMillis(200)))
                .thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("chat:batch:1"))))
                .thenReturn(Collections.emptyList());

        service.flushBatches();

        verifyNoInteractions(messagingTemplate);
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
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("chat:batch:1"))))
                .thenReturn(List.of(json1));
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("chat:batch:2"))))
                .thenReturn(List.of(json2));

        service.flushBatches();

        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.1"), any(ChatMessageDto.class));
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.2"), any(ChatMessageDto.class));
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
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("chat:batch:1"))))
                .thenReturn(List.of("{invalid json}", validJson));

        service.flushBatches();

        // Only the valid message should be sent
        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.1"), captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Valid");
    }

    @Test
    void flushBatches_AllCorruptedJson_ShouldNotSend() {
        when(setOps.members("chat:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("chat:batch:lock:1", "1", Duration.ofMillis(200)))
                .thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("chat:batch:1"))))
                .thenReturn(List.of("{bad1}", "{bad2}"));

        service.flushBatches();

        verifyNoInteractions(messagingTemplate);
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
