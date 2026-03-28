package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Deprecated
public class ChatBatchService {

    private static final int VIEWER_THRESHOLD = 50;

    private final SimpMessagingTemplate messagingTemplate;
    private final LiveViewerCounterService liveViewerCounterService;
    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<ChatMessageDto>> pendingMessages = new ConcurrentHashMap<>();

    public ChatBatchService(SimpMessagingTemplate messagingTemplate,
                            LiveViewerCounterService liveViewerCounterService) {
        this.messagingTemplate = messagingTemplate;
        this.liveViewerCounterService = liveViewerCounterService;
    }

    /**
     * Enqueue a chat message for broadcast. If the room has fewer than
     * {@link #VIEWER_THRESHOLD} viewers, the message is broadcast immediately.
     * Otherwise it is buffered and flushed by the scheduled batch window.
     */
    public void enqueueMessage(Long creatorUserId, ChatMessageDto message) {
        long viewerCount = liveViewerCounterService.getViewerCount(creatorUserId);

        if (viewerCount < VIEWER_THRESHOLD) {
            // Small room — broadcast immediately, no batching overhead
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorUserId, message);
            return;
        }

        pendingMessages.computeIfAbsent(creatorUserId, k -> new ConcurrentLinkedQueue<>()).add(message);
    }

    /**
     * Flush all buffered chat messages every 200ms. Each creatorId's queued
     * messages are sent as a single CHAT_BATCH payload.
     */
    public void flushBatches() {
        for (Map.Entry<Long, ConcurrentLinkedQueue<ChatMessageDto>> entry : pendingMessages.entrySet()) {
            Long creatorUserId = entry.getKey();
            ConcurrentLinkedQueue<ChatMessageDto> queue = entry.getValue();

            if (queue.isEmpty()) {
                continue;
            }

            // Drain the queue atomically — each poll() removes one element
            List<ChatMessageDto> batch = new ArrayList<>();
            ChatMessageDto msg;
            while ((msg = queue.poll()) != null) {
                batch.add(msg);
            }

            if (batch.isEmpty()) {
                continue;
            }

            if (batch.size() == 1) {
                // Single message — send as-is for backward compatibility
                messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorUserId, batch.get(0));
            } else {
                // Multiple messages — send as a batch
                Map<String, Object> batchPayload = Map.of(
                        "type", "CHAT_BATCH",
                        "messages", batch
                );
                messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorUserId, batchPayload);
            }
        }
    }

    /**
     * Remove the buffer for a creator (e.g., when a stream ends).
     */
    public void clearBuffer(Long creatorUserId) {
        pendingMessages.remove(creatorUserId);
    }
}
