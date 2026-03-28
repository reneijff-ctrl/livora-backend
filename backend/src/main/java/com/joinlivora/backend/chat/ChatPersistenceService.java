package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.domain.ChatMessage;
import com.joinlivora.backend.chat.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatPersistenceService {

    private final ChatMessageRepository chatMessageRepository;

    @Async("chatPersistenceExecutor")
    public void persistChatMessage(ChatMessage entity) {
        try {
            chatMessageRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to persist chat message for room {} from sender {}: {}",
                    entity.getRoomId(), entity.getSenderId(), e.getMessage(), e);
        }
    }
}
