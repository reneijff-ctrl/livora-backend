package com.joinlivora.backend.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreamEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private StreamEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new StreamEventPublisher(messagingTemplate);
    }

    // ── publishChatEvent ────────────────────────────────────────────────

    @Test
    void publishChatEvent_sendsToCompositeAndLegacyTopics() {
        Map<String, String> data = Map.of("message", "hello");

        publisher.publishChatEvent(42L, data);

        // Composite topic
        ArgumentCaptor<StreamEventPublisher.StreamEvent> captor =
                ArgumentCaptor.forClass(StreamEventPublisher.StreamEvent.class);
        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/stream.events.42"), captor.capture());

        StreamEventPublisher.StreamEvent event = captor.getValue();
        assertEquals("STREAM_EVENT", event.getType());
        assertEquals("chat", event.getChannel());
        assertEquals(data, event.getData());

        // Legacy topic
        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/chat.42"), eq(data));
    }

    // ── publishTipEvent ─────────────────────────────────────────────────

    @Test
    void publishTipEvent_sendsToCompositeAndLegacyTopics() {
        Map<String, Object> data = Map.of("amount", 100);

        publisher.publishTipEvent(7L, data);

        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/stream.events.7"), any(StreamEventPublisher.StreamEvent.class));
        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/monetization.7"), eq(data));
    }

    // ── publishGoalEvent ────────────────────────────────────────────────

    @Test
    void publishGoalEvent_sendsToCompositeAndLegacyTopics() {
        Map<String, Object> data = Map.of("progress", 75);

        publisher.publishGoalEvent(10L, data);

        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/stream.events.10"), any(StreamEventPublisher.StreamEvent.class));
        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/goals.10"), eq(data));
    }

    // ── publishLeaderboardEvent ─────────────────────────────────────────

    @Test
    void publishLeaderboardEvent_sendsToCompositeAndLegacyTopics() {
        Map<String, Object> data = Map.of("top", List.of("alice", "bob"));

        publisher.publishLeaderboardEvent(5L, data);

        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/stream.events.5"), any(StreamEventPublisher.StreamEvent.class));
        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/leaderboard.5"), eq(data));
    }

    // ── publishViewerEvent ──────────────────────────────────────────────

    @Test
    void publishViewerEvent_sendsToCompositeAndLegacyTopics() {
        Map<String, Object> data = Map.of("count", 250);

        publisher.publishViewerEvent(3L, data);

        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/stream.events.3"), any(StreamEventPublisher.StreamEvent.class));
        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/viewers.3"), eq(data));
    }

    // ── publishStreamStatusEvent ────────────────────────────────────────

    @Test
    void publishStreamStatusEvent_sendsToCompositeAndLegacyTopics() {
        Map<String, Object> data = Map.of("status", "LIVE");

        publisher.publishStreamStatusEvent(99L, data);

        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/stream.events.99"), any(StreamEventPublisher.StreamEvent.class));
        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/stream.v2.creator.99.status"), eq(data));
    }

    // ── Null safety ─────────────────────────────────────────────────────

    @Test
    void publish_nullCreatorId_doesNothing() {
        publisher.publishChatEvent(null, Map.of("msg", "hi"));
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void publish_nullData_doesNothing() {
        publisher.publishChatEvent(42L, null);
        verifyNoInteractions(messagingTemplate);
    }

    // ── publish with null legacy topic ──────────────────────────────────

    @Test
    void publish_nullLegacyTopic_onlySendsComposite() {
        publisher.publish(42L, "custom", Map.of("key", "val"), null);

        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/exchange/amq.topic/stream.events.42"), any(StreamEventPublisher.StreamEvent.class));
        verifyNoMoreInteractions(messagingTemplate);
    }

    // ── Error resilience ────────────────────────────────────────────────

    @Test
    void publish_compositeThrows_stillSendsLegacy() {
        doThrow(new RuntimeException("broker down"))
                .doNothing()
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        publisher.publishChatEvent(1L, Map.of("msg", "test"));

        // Both sends attempted despite first throwing
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void publish_legacyThrows_doesNotCrash() {
        doNothing()
                .doThrow(new RuntimeException("legacy down"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        assertDoesNotThrow(() -> publisher.publishChatEvent(1L, Map.of("msg", "test")));
    }

    // ── publishCompositeEvent ───────────────────────────────────────────

    @Test
    void publishCompositeEvent_sendsAllEventsAsBatch() {
        List<StreamEventPublisher.CompositeEntry> entries = List.of(
                StreamEventPublisher.CompositeEntry.builder()
                        .channel("chat").data(Map.of("msg", "hi"))
                        .legacyTopic("/exchange/amq.topic/chat.1").build(),
                StreamEventPublisher.CompositeEntry.builder()
                        .channel("tip").data(Map.of("amount", 50))
                        .legacyTopic("/exchange/amq.topic/monetization.1").build()
        );

        publisher.publishCompositeEvent(1L, entries);

        // 1 composite batch + 2 legacy sends = 3 total
        verify(messagingTemplate, times(3)).convertAndSend(anyString(), any(Object.class));

        // Verify composite batch structure
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> batchCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/stream.events.1"), batchCaptor.capture());

        Map<String, Object> batch = batchCaptor.getValue();
        assertEquals("STREAM_EVENT_BATCH", batch.get("type"));

        @SuppressWarnings("unchecked")
        List<StreamEventPublisher.StreamEvent> events =
                (List<StreamEventPublisher.StreamEvent>) batch.get("events");
        assertEquals(2, events.size());
        assertEquals("chat", events.get(0).getChannel());
        assertEquals("tip", events.get(1).getChannel());
    }

    @Test
    void publishCompositeEvent_nullCreatorId_doesNothing() {
        List<StreamEventPublisher.CompositeEntry> entries = List.of(
                StreamEventPublisher.CompositeEntry.builder()
                        .channel("chat").data(Map.of("msg", "hi")).build()
        );

        publisher.publishCompositeEvent(null, entries);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void publishCompositeEvent_nullList_doesNothing() {
        publisher.publishCompositeEvent(1L, null);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void publishCompositeEvent_emptyList_doesNothing() {
        publisher.publishCompositeEvent(1L, Collections.emptyList());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void publishCompositeEvent_entryWithNullLegacyTopic_skipsLegacySend() {
        List<StreamEventPublisher.CompositeEntry> entries = List.of(
                StreamEventPublisher.CompositeEntry.builder()
                        .channel("chat").data(Map.of("msg", "hi"))
                        .legacyTopic(null).build()
        );

        publisher.publishCompositeEvent(1L, entries);

        // Only composite batch — no legacy send
        verify(messagingTemplate, times(1)).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void publishCompositeEvent_entryWithNullData_skipsLegacySend() {
        List<StreamEventPublisher.CompositeEntry> entries = List.of(
                StreamEventPublisher.CompositeEntry.builder()
                        .channel("chat").data(null)
                        .legacyTopic("/exchange/amq.topic/chat.1").build()
        );

        publisher.publishCompositeEvent(1L, entries);

        // Composite batch sent (with null data inside), but legacy not sent
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/exchange/amq.topic/stream.events.1"), any(Map.class));
    }

    @Test
    void publishCompositeEvent_legacySendThrows_doesNotCrash() {
        doNothing()
                .doThrow(new RuntimeException("legacy fail"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        List<StreamEventPublisher.CompositeEntry> entries = List.of(
                StreamEventPublisher.CompositeEntry.builder()
                        .channel("tip").data(Map.of("a", 1))
                        .legacyTopic("/exchange/amq.topic/monetization.1").build()
        );

        assertDoesNotThrow(() -> publisher.publishCompositeEvent(1L, entries));
    }

    // ── StreamEvent DTO ─────────────────────────────────────────────────

    @Test
    void streamEvent_builderSetsAllFields() {
        StreamEventPublisher.StreamEvent event = StreamEventPublisher.StreamEvent.builder()
                .type("STREAM_EVENT")
                .channel("goal")
                .data(Map.of("progress", 80))
                .build();

        assertEquals("STREAM_EVENT", event.getType());
        assertEquals("goal", event.getChannel());
        assertEquals(Map.of("progress", 80), event.getData());
    }

    // ── Channel constants ───────────────────────────────────────────────

    @Test
    void channelConstants_haveExpectedValues() {
        assertEquals("chat", StreamEventPublisher.CHANNEL_CHAT);
        assertEquals("tip", StreamEventPublisher.CHANNEL_TIP);
        assertEquals("goal", StreamEventPublisher.CHANNEL_GOAL);
        assertEquals("leaderboard", StreamEventPublisher.CHANNEL_LEADERBOARD);
        assertEquals("viewers", StreamEventPublisher.CHANNEL_VIEWERS);
        assertEquals("stream_status", StreamEventPublisher.CHANNEL_STREAM_STATUS);
    }
}
