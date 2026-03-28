package com.joinlivora.backend.moderation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModerationAuditLogRepository extends JpaRepository<ModerationAuditLog, UUID> {

    @Query("SELECT a FROM ModerationAuditLog a WHERE a.creatorId = :creatorId ORDER BY a.createdAt DESC")
    List<ModerationAuditLog> findByCreatorIdOrderByCreatedAtDesc(@Param("creatorId") Long creatorId);

    @Query(value = "SELECT a FROM ModerationAuditLog a WHERE a.creatorId = :creatorId ORDER BY a.createdAt DESC LIMIT :limit")
    List<ModerationAuditLog> findRecentByCreatorId(@Param("creatorId") Long creatorId, @Param("limit") int limit);
}
