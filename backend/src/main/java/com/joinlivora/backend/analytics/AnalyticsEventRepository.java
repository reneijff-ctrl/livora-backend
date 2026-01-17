package com.joinlivora.backend.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    @Query("SELECT COUNT(DISTINCT e.funnelId) FROM AnalyticsEvent e WHERE e.eventType = 'VISIT' AND e.createdAt >= :since")
    long countUniqueVisits(@Param("since") Instant since);

    @Query("SELECT COUNT(e) FROM AnalyticsEvent e WHERE e.eventType = 'USER_REGISTERED' AND e.createdAt >= :since")
    long countRegistrations(@Param("since") Instant since);

    @Query("SELECT COUNT(DISTINCT e.user.id) FROM AnalyticsEvent e WHERE e.eventType = 'SUBSCRIPTION_STARTED' AND e.createdAt >= :since")
    long countNewSubscriptions(@Param("since") Instant since);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM AnalyticsEvent e WHERE e.createdAt < :cutoff")
    void deleteOlderThan(@Param("cutoff") Instant cutoff);

    @Query(value = "SELECT e.metadata->>'variant' as variant, COUNT(e) as count " +
           "FROM analytics_events e " +
           "WHERE e.event_type = 'EXPERIMENT_ASSIGNED' " +
           "AND e.metadata->>'experimentKey' = :experimentKey " +
           "GROUP BY e.metadata->>'variant'", nativeQuery = true)
    java.util.List<Object[]> countByExperimentKeyAndVariant(@Param("experimentKey") String experimentKey);
}
