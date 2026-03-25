package com.joinlivora.backend.chat.repository;

import com.joinlivora.backend.chat.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findTopByRoomIdOrderByCreatedAtDesc(Long roomId);
    
    List<ChatMessage> findTop50ByRoomIdOrderByCreatedAtDesc(Long roomId);
    
    Page<ChatMessage> findByRoomId(Long roomId, Pageable pageable);

    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(Long roomId);
}
