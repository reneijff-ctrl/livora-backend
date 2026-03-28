package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatBatchServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private LiveViewerCounterService liveViewerCounterService;

    private ChatBatchService chatBatchService;

    @BeforeEach
    void setUp() {
        chatBatchService = new ChatBatchService(messagingTemplate, liveViewerCounterService);
    }

    @Test
    void enqueueMessage_SmallRoom_ShouldBroadcastImmediately() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(30L);

        ChatMessageDto msg = buildMessage("Hello");
        chatBatchService.enqueueMessage(1L, msg);

        verify(messagingTemplate).convertAndSend("/exchange/amq.topic/chat.1", msg);
    }

    @Test
    void enqueueMessage_LargeRoom_ShouldBuffer() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(100L);

        ChatMessageDto msg = buildMessage("Hello");
        chatBatchService.enqueueMessage(1L, msg);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void flushBatches_SingleMessage_ShouldSendAsIs() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(100L);

        ChatMessageDto msg = buildMessage("Hello");
        chatBatchService.enqueueMessage(1L, msg);
        chatBatchService.flushBatches();

        verify(messagingTemplate).convertAndSend("/exchange/amq.topic/chat.1", msg);
    }

    @Test
    @SuppressWarnings("unchecked")
    void flushBatches_MultipleMessages_ShouldSendBatch() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(200L);

        ChatMessageDto msg1 = buildMessage("Hello");
        ChatMessageDto msg2 = buildMessage("World");
        ChatMessageDto msg3 = buildMessage("!");

        chatBatchService.enqueueMessage(1L, msg1);
        chatBatchService.enqueueMessage(1L, msg2);
        chatBatchService.enqueueMessage(1L, msg3);

        chatBatchService.flushBatches();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.1"), captor.capture());

        Map<String, Object> batch = captor.getValue();
        assertThat(batch.get("type")).isEqualTo("CHAT_BATCH");
        assertThat((List<?>) batch.get("messages")).hasSize(3);
    }

    @Test
    void flushBatches_EmptyBuffer_ShouldNotSend() {
        chatBatchService.flushBatches();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void flushBatches_ShouldDrainBuffer() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(100L);

        chatBatchService.enqueueMessage(1L, buildMessage("First"));
        chatBatchService.flushBatches();

        // Second flush should not send anything
        reset(messagingTemplate);
        chatBatchService.flushBatches();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void flushBatches_MultipleCreators_ShouldFlushSeparately() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(100L);
        when(liveViewerCounterService.getViewerCount(2L)).thenReturn(100L);

        chatBatchService.enqueueMessage(1L, buildMessage("Room1"));
        chatBatchService.enqueueMessage(2L, buildMessage("Room2"));

        chatBatchService.flushBatches();

        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.1"), any(ChatMessageDto.class));
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.2"), any(ChatMessageDto.class));
    }

    @Test
    void clearBuffer_ShouldRemoveCreatorMessages() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(200L);

        chatBatchService.enqueueMessage(1L, buildMessage("Hello"));
        chatBatchService.clearBuffer(1L);
        chatBatchService.flushBatches();

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void enqueueMessage_ExactlyAtThreshold_ShouldBroadcastImmediately() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(49L);

        ChatMessageDto msg = buildMessage("At threshold");
        chatBatchService.enqueueMessage(1L, msg);

        verify(messagingTemplate).convertAndSend("/exchange/amq.topic/chat.1", msg);
    }

    @Test
    void enqueueMessage_AtThresholdBoundary_ShouldBuffer() {
        when(liveViewerCounterService.getViewerCount(1L)).thenReturn(50L);

        ChatMessageDto msg = buildMessage("At boundary");
        chatBatchService.enqueueMessage(1L, msg);

        verifyNoInteractions(messagingTemplate);
    }

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
