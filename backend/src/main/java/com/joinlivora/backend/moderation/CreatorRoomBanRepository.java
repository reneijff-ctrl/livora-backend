package com.joinlivora.backend.moderation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorRoomBanRepository extends JpaRepository<CreatorRoomBan, UUID> {

    @Query("SELECT b FROM CreatorRoomBan b WHERE b.creatorId = :creatorId AND b.targetUser.id = :targetUserId " +
           "AND b.active = true AND (b.expiresAt IS NULL OR b.expiresAt > :now)")
    Optional<CreatorRoomBan> findActiveBan(@Param("creatorId") Long creatorId,
                                           @Param("targetUserId") Long targetUserId,
                                           @Param("now") Instant now);

    @Query("SELECT b FROM CreatorRoomBan b WHERE b.creatorId = :creatorId " +
           "AND b.active = true AND (b.expiresAt IS NULL OR b.expiresAt > :now) " +
           "ORDER BY b.createdAt DESC")
    List<CreatorRoomBan> findActiveBansByCreator(@Param("creatorId") Long creatorId,
                                                  @Param("now") Instant now);

    @Query("SELECT b FROM CreatorRoomBan b WHERE b.creatorId = :creatorId ORDER BY b.createdAt DESC")
    List<CreatorRoomBan> findAllByCreatorId(@Param("creatorId") Long creatorId);

    default boolean isUserBanned(Long creatorId, Long targetUserId) {
        return findActiveBan(creatorId, targetUserId, Instant.now()).isPresent();
    }
}
