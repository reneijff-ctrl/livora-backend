package com.joinlivora.backend.abuse.repository;

import com.joinlivora.backend.abuse.model.UserRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRestrictionRepository extends JpaRepository<UserRestriction, UUID> {
    Optional<UserRestriction> findByUserId(UUID userId);

    @Query("SELECT r FROM UserRestriction r WHERE r.userId = :userId AND (r.expiresAt IS NULL OR r.expiresAt > :now)")
    Optional<UserRestriction> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("SELECT COUNT(r) > 0 FROM UserRestriction r WHERE r.userId = :userId AND (r.expiresAt IS NULL OR r.expiresAt > :now)")
    boolean existsActiveRestriction(@Param("userId") UUID userId, @Param("now") Instant now);

    void deleteByExpiresAtBefore(Instant now);
}
