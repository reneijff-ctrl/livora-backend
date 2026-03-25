package com.joinlivora.backend.creator.repository;

import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, Long> {
    Optional<CreatorProfile> findByUserId(Long userId);
    long countByUserId(Long userId);

    Optional<CreatorProfile> findByUsername(String username);

    Optional<CreatorProfile> findByPublicHandle(String publicHandle);

    @Query("SELECT cp FROM CreatorProfile cp JOIN FETCH cp.user u WHERE u.id = :userId")
    Optional<CreatorProfile> findByUserIdWithUser(@Param("userId") Long userId);

    Optional<CreatorProfile> findByUser(User user);
    void deleteByUser(User user);

    @Query("SELECT cp FROM CreatorProfile cp WHERE cp.user IN :users")
    java.util.List<CreatorProfile> findAllByUserIn(@Param("users") java.util.List<User> users);

    @Query("SELECT cp FROM CreatorProfile cp " +
           "JOIN cp.user u " +
           "JOIN Creator c ON cp.user.id = c.user.id " +
           "LEFT JOIN Stream s ON u = s.creator AND s.isLive = true " +
           "LEFT JOIN CreatorPresence p ON c.id = p.creatorId " +
           "LEFT JOIN CreatorEarning ce ON u = ce.creator " +
           "WHERE u.role = com.joinlivora.backend.user.Role.CREATOR " +
           "AND (s.isLive = true OR :onlyLive = false) " +
           "AND ( (p.online = true AND p.lastSeen > :since) OR :onlyLive = false ) " +
           "AND (cp.visibility = com.joinlivora.backend.creator.model.ProfileVisibility.PUBLIC OR :onlyLive = false) " +
           "AND u.shadowbanned = false " +
           "GROUP BY cp.id, u.id, s.id, c.id, p.id " +
           "ORDER BY COALESCE(s.isLive, false) DESC, COALESCE(p.online, false) DESC, COALESCE(SUM(ce.netAmount), 0) DESC")
    Page<CreatorProfile> findExploreCreators(@Param("since") Instant since, @Param("onlyLive") boolean onlyLive, Pageable pageable);
}
