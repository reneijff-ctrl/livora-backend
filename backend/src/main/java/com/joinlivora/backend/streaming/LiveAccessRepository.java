package com.joinlivora.backend.streaming;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * LiveAccessRepository - Data access layer for LiveAccess.
 */
@Repository
public interface LiveAccessRepository extends JpaRepository<LiveAccess, Long> {
    Optional<LiveAccess> findByCreatorUserIdAndViewerUserId(Long creatorUserId, Long viewerUserId);
}
