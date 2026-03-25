package com.joinlivora.backend.livestream.repository;

import com.joinlivora.backend.livestream.domain.LivestreamSession;
import com.joinlivora.backend.livestream.domain.LivestreamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LivestreamSessionRepository extends JpaRepository<LivestreamSession, Long> {

    Optional<LivestreamSession> findTopByCreator_IdAndStatusOrderByStartedAtDesc(Long creatorId, LivestreamStatus status);

    java.util.List<LivestreamSession> findAllByStatus(LivestreamStatus status);

    boolean existsByCreator_IdAndStatus(Long creatorId, LivestreamStatus status);

    Optional<LivestreamSession> findTopByCreator_IdOrderByStartedAtDesc(Long creatorId);
    
    long countByCreator_Id(Long creatorId);

    org.springframework.data.domain.Page<LivestreamSession> findAllByStartedAtIsNotNull(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT FUNCTION('date_trunc', 'hour', s.startedAt), COUNT(s) FROM LivestreamSession s " +
           "WHERE s.startedAt >= :after GROUP BY FUNCTION('date_trunc', 'hour', s.startedAt) ORDER BY FUNCTION('date_trunc', 'hour', s.startedAt)")
    java.util.List<Object[]> countStreamsGroupedByHour(@Param("after") java.time.LocalDateTime after);
}
