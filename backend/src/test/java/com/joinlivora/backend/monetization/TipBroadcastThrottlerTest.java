package com.joinlivora.backend.monetization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TipBroadcastThrottlerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ListOperations<String, String> listOps;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    private ValueOperations<String, String> valueOps;

    private ObjectMapper objectMapper;
    private TipBroadcastThrottler throttler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        throttler = new TipBroadcastThrottler(redisTemplate, messagingTemplate, objectMapper);
    }

    // --- enqueueTipEvent tests ---

    @Test
    void enqueueTipEvent_ShouldBufferInRedis() throws JsonProcessingException {
        Map<String, Object> event = Map.of("type", "TIP", "amount", 50);

        throttler.enqueueTipEvent(1L, event);

        verify(listOps).rightPush(eq("tip:batch:1"), anyString());
        verify(redisTemplate).expire("tip:batch:1", Duration.ofSeconds(5));
        verify(setOps).add("tip:batch:active", "1");
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void enqueueTipEvent_NullCreatorId_ShouldDoNothing() {
        throttler.enqueueTipEvent(null, Map.of("type", "TIP"));

        verifyNoInteractions(messagingTemplate, listOps, setOps);
    }

    @Test
    void enqueueTipEvent_NullEvent_ShouldDoNothing() {
        throttler.enqueueTipEvent(1L, null);

        verifyNoInteractions(messagingTemplate, listOps, setOps);
    }

    @Test
    void enqueueTipEvent_RedisError_ShouldFallbackToDirectSend() {
        Map<String, Object> event = Map.of("type", "TIP", "amount", 50);
        when(listOps.rightPush(anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        throttler.enqueueTipEvent(1L, event);

        verify(messagingTemplate).convertAndSend("/exchange/amq.topic/monetization.1", event);
    }

    @Test
    void enqueueTipEvent_MultipleCreators_ShouldBufferSeparately() {
        Map<String, Object> event1 = Map.of("type", "TIP", "creator", 1);
        Map<String, Object> event2 = Map.of("type", "TIP", "creator", 2);

        throttler.enqueueTipEvent(1L, event1);
        throttler.enqueueTipEvent(2L, event2);

        verify(listOps).rightPush(eq("tip:batch:1"), anyString());
        verify(listOps).rightPush(eq("tip:batch:2"), anyString());
        verify(setOps).add("tip:batch:active", "1");
        verify(setOps).add("tip:batch:active", "2");
    }

    // --- flushBatches tests ---

    @Test
    void flushBatches_EmptyActiveSet_ShouldNotSend() {
        when(setOps.members("tip:batch:active")).thenReturn(Collections.emptySet());

        throttler.flushBatches();

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void flushBatches_NullActiveSet_ShouldNotSend() {
        when(setOps.members("tip:batch:active")).thenReturn(null);

        throttler.flushBatches();

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void flushBatches_SingleEvent_ShouldSendDirectly() throws JsonProcessingException {
        Map<String, Object> event = Map.of("type", "TIP", "amount", 50);
        String json = objectMapper.writeValueAsString(event);

        when(setOps.members("tip:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("tip:batch:lock:1", "1", Duration.ofMillis(500)))
                .thenReturn(true);
        when(listOps.range("tip:batch:1", 0, -1)).thenReturn(List.of(json));
        when(redisTemplate.delete("tip:batch:1")).thenReturn(true);

        throttler.flushBatches();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/monetization.1"), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(Map.class);
        assertThat(((Map<String, Object>) captor.getValue()).get("type")).isEqualTo("TIP");
    }

    @Test
    @SuppressWarnings("unchecked")
    void flushBatches_MultipleEvents_ShouldSendBatch() throws JsonProcessingException {
        Map<String, Object> event1 = Map.of("type", "TIP", "amount", 50);
        Map<String, Object> event2 = Map.of("type", "TIP", "amount", 100);
        Map<String, Object> event3 = Map.of("type", "ACTION_TRIGGERED", "amount", 50);
        String json1 = objectMapper.writeValueAsString(event1);
        String json2 = objectMapper.writeValueAsString(event2);
        String json3 = objectMapper.writeValueAsString(event3);

        when(setOps.members("tip:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("tip:batch:lock:1", "1", Duration.ofMillis(500)))
                .thenReturn(true);
        when(listOps.range("tip:batch:1", 0, -1)).thenReturn(List.of(json1, json2, json3));
        when(redisTemplate.delete("tip:batch:1")).thenReturn(true);

        throttler.flushBatches();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/monetization.1"), captor.capture());

        Map<String, Object> batch = captor.getValue();
        assertThat(batch.get("type")).isEqualTo("TIP_BATCH");
        assertThat((List<?>) batch.get("events")).hasSize(3);
    }

    @Test
    void flushBatches_LockNotAcquired_ShouldSkipCreator() {
        when(setOps.members("tip:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("tip:batch:lock:1", "1", Duration.ofMillis(500)))
                .thenReturn(false);

        throttler.flushBatches();

        verifyNoInteractions(messagingTemplate);
        verify(listOps, never()).range(anyString(), anyLong(), anyLong());
    }

    @Test
    void flushBatches_EmptyBatchKey_ShouldNotSend() {
        when(setOps.members("tip:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("tip:batch:lock:1", "1", Duration.ofMillis(500)))
                .thenReturn(true);
        when(listOps.range("tip:batch:1", 0, -1)).thenReturn(Collections.emptyList());
        when(redisTemplate.delete("tip:batch:1")).thenReturn(true);

        throttler.flushBatches();

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void flushBatches_MultipleCreators_ShouldFlushSeparately() throws JsonProcessingException {
        Map<String, Object> event1 = Map.of("type", "TIP", "creator", 1);
        Map<String, Object> event2 = Map.of("type", "TIP", "creator", 2);
        String json1 = objectMapper.writeValueAsString(event1);
        String json2 = objectMapper.writeValueAsString(event2);

        when(setOps.members("tip:batch:active")).thenReturn(Set.of("1", "2"));
        when(valueOps.setIfAbsent(eq("tip:batch:lock:1"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(valueOps.setIfAbsent(eq("tip:batch:lock:2"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(listOps.range("tip:batch:1", 0, -1)).thenReturn(List.of(json1));
        when(listOps.range("tip:batch:2", 0, -1)).thenReturn(List.of(json2));
        when(redisTemplate.delete(anyString())).thenReturn(true);

        throttler.flushBatches();

        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/monetization.1"), any(Object.class));
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/monetization.2"), any(Object.class));
    }

    @Test
    void flushBatches_InvalidCreatorId_ShouldRemoveFromActiveSet() {
        when(setOps.members("tip:batch:active")).thenReturn(Set.of("not-a-number"));

        throttler.flushBatches();

        verify(setOps).remove("tip:batch:active", "not-a-number");
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void flushBatches_RedisReadError_ShouldNotCrash() {
        when(setOps.members("tip:batch:active"))
                .thenThrow(new RuntimeException("Redis down"));

        throttler.flushBatches();

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void flushBatches_CorruptedJson_ShouldSkipBadEvents() throws JsonProcessingException {
        Map<String, Object> validEvent = Map.of("type", "TIP", "amount", 50);
        String validJson = objectMapper.writeValueAsString(validEvent);

        when(setOps.members("tip:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("tip:batch:lock:1", "1", Duration.ofMillis(500)))
                .thenReturn(true);
        when(listOps.range("tip:batch:1", 0, -1))
                .thenReturn(List.of("{invalid json}", validJson));
        when(redisTemplate.delete("tip:batch:1")).thenReturn(true);

        throttler.flushBatches();

        // Only the valid event should be sent (single → direct)
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/monetization.1"), any(Object.class));
    }

    @Test
    void flushBatches_AllCorruptedJson_ShouldNotSend() {
        when(setOps.members("tip:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("tip:batch:lock:1", "1", Duration.ofMillis(500)))
                .thenReturn(true);
        when(listOps.range("tip:batch:1", 0, -1))
                .thenReturn(List.of("{bad1}", "{bad2}"));
        when(redisTemplate.delete("tip:batch:1")).thenReturn(true);

        throttler.flushBatches();

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void flushBatches_NullBatchList_ShouldNotSend() {
        when(setOps.members("tip:batch:active")).thenReturn(Set.of("1"));
        when(valueOps.setIfAbsent("tip:batch:lock:1", "1", Duration.ofMillis(500)))
                .thenReturn(true);
        when(listOps.range("tip:batch:1", 0, -1)).thenReturn(null);
        when(redisTemplate.delete("tip:batch:1")).thenReturn(true);

        throttler.flushBatches();

        verifyNoInteractions(messagingTemplate);
    }

    // --- clearBuffer tests ---

    @Test
    void clearBuffer_ShouldDeleteKeyAndRemoveFromActiveSet() {
        when(redisTemplate.delete("tip:batch:1")).thenReturn(true);

        throttler.clearBuffer(1L);

        verify(redisTemplate).delete("tip:batch:1");
        verify(setOps).remove("tip:batch:active", "1");
    }

    @Test
    void clearBuffer_NullCreatorId_ShouldDoNothing() {
        throttler.clearBuffer(null);

        verify(redisTemplate, never()).delete(anyString());
    }
}
