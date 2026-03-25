package com.joinlivora.backend.chat;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ModerationRepository extends JpaRepository<Moderation, Long> {
    Optional<Moderation> findTopByTargetUserAndActionAndExpiresAtAfterOrderByCreatedAtDesc(
            User targetUser, ModerationAction action, Instant now);

    boolean existsByTargetUserAndActionAndRoomId(User targetUser, ModerationAction action, String roomId);
    
    Optional<Moderation> findTopByTargetUserAndActionAndRoomIdOrderByCreatedAtDesc(
            User targetUser, ModerationAction action, String roomId);
}
