package com.joinlivora.backend.presence.repository;

import com.joinlivora.backend.creator.dto.OnlineCreatorDto;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.presence.entity.CreatorPresence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreatorPresenceRepository extends JpaRepository<CreatorPresence, Long> {

    Optional<CreatorPresence> findByCreatorId(Long creatorId);

    List<CreatorPresence> findByOnlineTrue();

    @Query("""
    SELECT cp FROM CreatorProfile cp
    JOIN Creator c ON cp.user.id = c.user.id
    LEFT JOIN CreatorPresence p ON c.id = p.creatorId
    JOIN cp.user u
    WHERE ( (p.online = true AND p.lastSeen > :threshold) OR :includeOffline = true )
      AND (cp.visibility = com.joinlivora.backend.creator.model.ProfileVisibility.PUBLIC OR :includeOffline = true)
      AND u.shadowbanned = false
      AND u.role = com.joinlivora.backend.user.Role.CREATOR
    """)
    List<CreatorProfile> findOnlineCreators(@Param("threshold") Instant threshold, @Param("includeOffline") boolean includeOffline);

    @Query("""
    SELECT cp FROM CreatorProfile cp
    JOIN Creator c ON cp.user.id = c.user.id
    LEFT JOIN CreatorPresence p ON c.id = p.creatorId
    JOIN cp.user u
    WHERE ( (p.online = true AND p.lastSeen > :threshold) OR :includeOffline = true )
      AND (cp.visibility = com.joinlivora.backend.creator.model.ProfileVisibility.PUBLIC OR :includeOffline = true)
      AND u.shadowbanned = false
      AND u.role = com.joinlivora.backend.user.Role.CREATOR
    """)
    Page<CreatorProfile> findOnlineCreators(@Param("threshold") Instant threshold, @Param("includeOffline") boolean includeOffline, Pageable pageable);

    @Query("""
    SELECT new com.joinlivora.backend.creator.dto.OnlineCreatorDto(
      p.creatorId,
      cp.username,
      cp.displayName,
      p.online,
      p.lastSeen
    )
    FROM CreatorPresence p
    JOIN Creator c ON p.creatorId = c.id
    JOIN CreatorProfile cp ON c.user.id = cp.user.id
    """)
    List<OnlineCreatorDto> findAllOnlineCreators();
}
