package com.joinlivora.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PPVChatAccessRepository extends JpaRepository<PPVChatAccess, UUID> {

    @Query("SELECT COUNT(p) > 0 FROM PPVChatAccess p WHERE p.userId.id = :userId AND p.roomId.id = :roomId AND (p.expiresAt IS NULL OR p.expiresAt > :now)")
    boolean existsActiveAccess(@Param("userId") Long userId, @Param("roomId") UUID roomId, @Param("now") Instant now);

    @Query("SELECT p FROM PPVChatAccess p WHERE p.userId.id = :userId AND p.roomId.id = :roomId")
    List<PPVChatAccess> findByUserAndRoom(@Param("userId") Long userId, @Param("roomId") UUID roomId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM PPVChatAccess p WHERE p.userId.id = :userId AND p.roomId.id = :roomId")
    void deleteByUserAndRoom(@Param("userId") Long userId, @Param("roomId") UUID roomId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM PPVChatAccess p WHERE p.expiresAt IS NOT NULL AND p.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
