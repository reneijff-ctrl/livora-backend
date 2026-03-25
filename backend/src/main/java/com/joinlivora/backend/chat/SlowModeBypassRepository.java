package com.joinlivora.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlowModeBypassRepository extends JpaRepository<SlowModeBypass, UUID> {

    @Query("SELECT s FROM SlowModeBypass s WHERE s.userId.id = :userId AND s.roomId.id = :roomId AND s.expiresAt > :now")
    Optional<SlowModeBypass> findActiveByUserIdAndRoomId(@Param("userId") Long userId, @Param("roomId") UUID roomId, @Param("now") Instant now);

    java.util.List<SlowModeBypass> findAllByExpiresAtBefore(Instant now);

    @Modifying
    @Transactional
    @Query("DELETE FROM SlowModeBypass s WHERE s.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
