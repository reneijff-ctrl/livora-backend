package com.joinlivora.backend.chat.repository;

import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.domain.ChatRoomStatus;
import com.joinlivora.backend.chat.domain.ChatRoomType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository("chatRoomRepositoryV2")
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    
    Optional<ChatRoom> findByCreatorId(Long creatorId);

    @Query("""
        SELECT c FROM ChatRoomV2 c
        WHERE c.creatorId = :creatorId
        AND c.name NOT LIKE CONCAT(:prefix, '%')
    """)
    Optional<ChatRoom> findMainRoom(@Param("creatorId") Long creatorId, @Param("prefix") String prefix);
    Optional<ChatRoom> findByName(String name);
    Optional<ChatRoom> findByCreatorIdAndStatus(Long creatorId, ChatRoomStatus status);
    List<ChatRoom> findAllByCreatorIdAndStatus(Long creatorId, ChatRoomStatus status);
    List<ChatRoom> findAllByCreatorIdAndStatusIn(Long creatorId, java.util.Collection<ChatRoomStatus> statuses);
    
    Page<ChatRoom> findAllByCreatorId(Long creatorId, Pageable pageable);
    
    Optional<ChatRoom> findByPpvContentId(UUID ppvContentId);
    
    List<ChatRoom> findAllByIsLiveTrue();

    Optional<ChatRoom> findByCreatorIdAndViewerIdAndRoomType(Long creatorId, Long viewerId, ChatRoomType roomType);

    Optional<ChatRoom> findByCreatorIdAndViewerIdAndRoomTypeAndStatus(Long creatorId, Long viewerId, ChatRoomType roomType, ChatRoomStatus status);

    List<ChatRoom> findByCreatorIdAndRoomType(Long creatorId, ChatRoomType roomType);

    List<ChatRoom> findByViewerIdAndRoomType(Long viewerId, ChatRoomType roomType);
}
