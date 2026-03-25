package com.joinlivora.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @deprecated Use com.joinlivora.backend.chat.repository.ChatRoomRepository instead.
 * TODO: Remove this repository after consolidating chat domain.
 */
@Deprecated
@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
    Optional<ChatRoom> findByName(String name);
    List<ChatRoom> findAllByCreatedBy_Id(Long createdById);
    Optional<ChatRoom> findByPpvContent_Id(UUID ppvContentId);
    List<ChatRoom> findAllByIsLiveTrue();
}
