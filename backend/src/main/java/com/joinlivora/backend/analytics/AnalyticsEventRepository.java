package com.joinlivora.backend.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    @Query("SELECT COUNT(DISTINCT e.funnelId) FROM AnalyticsEvent e WHERE e.eventType = 'VISIT' AND e.createdAt >= :since")
    long countUniqueVisits(@Param("since") Instant since);

    @Query("SELECT COUNT(e) FROM AnalyticsEvent e WHERE e.eventType = 'USER_REGISTERED' AND e.createdAt >= :since")
    long countRegistrations(@Param("since") Instant since);

    @Query("SELECT COUNT(DISTINCT e.user.id) FROM AnalyticsEvent e WHERE e.eventType = 'SUBSCRIPTION_STARTED' AND e.createdAt >= :since")
    long countNewSubscriptions(@Param("since") Instant since);

    @Query("SELECT COUNT(DISTINCT e.user.id) FROM AnalyticsEvent e WHERE e.eventType = 'SUBSCRIPTION_STARTED' AND e.createdAt BETWEEN :start AND :end")
    long countNewSubscriptionsByPeriod(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COUNT(DISTINCT e.user.id) FROM AnalyticsEvent e WHERE e.eventType = 'SUBSCRIPTION_CANCELED' AND e.createdAt BETWEEN :start AND :end")
    long countCanceledSubscriptionsByPeriod(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COUNT(DISTINCT e.funnelId) FROM AnalyticsEvent e WHERE e.eventType = 'VISIT' AND e.createdAt BETWEEN :start AND :end")
    long countUniqueVisitsByPeriod(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COUNT(e) FROM AnalyticsEvent e WHERE e.eventType = 'USER_REGISTERED' AND e.createdAt BETWEEN :start AND :end")
    long countRegistrationsByPeriod(@Param("start") Instant start, @Param("end") Instant end);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM AnalyticsEvent e WHERE e.createdAt < :cutoff")
    void deleteOlderThan(@Param("cutoff") Instant cutoff);

    @Query(value = "SELECT e.metadata->>'variant' as variant, COUNT(e) as count " +
           "FROM analytics_events e " +
           "WHERE e.event_type = 'EXPERIMENT_ASSIGNED' " +
           "AND e.metadata->>'experimentKey' = :experimentKey " +
           "GROUP BY e.metadata->>'variant'", nativeQuery = true)
    List<Object[]> countByExperimentKeyAndVariant(@Param("experimentKey") String experimentKey);

    @Query(value = "SELECT DISTINCT e.metadata->>'experimentKey' FROM analytics_events e WHERE e.event_type = 'EXPERIMENT_ASSIGNED'", nativeQuery = true)
    List<String> findActiveExperimentKeys();

    @Query(value = "SELECT COUNT(DISTINCT e.user_id) FROM analytics_events e " +
           "WHERE e.event_type = 'STREAM_JOIN' " +
           "AND e.metadata->>'creator' = :creatorId " +
           "AND e.created_at BETWEEN :start AND :end", nativeQuery = true)
    long countUniqueViewersByCreatorAndPeriod(@Param("creator") String creatorId, @Param("start") Instant start, @Param("end") Instant end);

    @Query(value = "SELECT COUNT(DISTINCT e.user_id) FROM analytics_events e " +
           "WHERE e.event_type = 'STREAM_JOIN' " +
           "AND e.metadata->>'creator' = :creatorId " +
           "AND e.created_at BETWEEN :start AND :end " +
           "AND e.user_id IS NOT NULL " +
           "AND EXISTS (" +
           "  SELECT 1 FROM analytics_events e2 " +
           "  WHERE e2.user_id = e.user_id " +
           "  AND e2.event_type = 'STREAM_JOIN' " +
           "  AND e2.metadata->>'creator' = :creatorId " +
           "  AND e2.created_at < :start" +
           ")", nativeQuery = true)
    long countReturningViewersByCreatorAndPeriod(@Param("creator") String creatorId, @Param("start") Instant start, @Param("end") Instant end);

    @Query(value = "SELECT COALESCE(SUM(CAST(e.metadata->>'duration' AS BIGINT)), 0) FROM analytics_events e " +
           "WHERE e.event_type = 'STREAM_LEAVE' " +
           "AND e.metadata->>'creator' = :creatorId " +
           "AND e.created_at BETWEEN :start AND :end", nativeQuery = true)
    long sumSessionDurationByCreatorAndPeriod(@Param("creator") String creatorId, @Param("start") Instant start, @Param("end") Instant end);

    @Query(value = "SELECT COUNT(e.id) FROM analytics_events e " +
           "WHERE e.event_type = 'STREAM_LEAVE' " +
           "AND e.metadata->>'creator' = :creatorId " +
           "AND e.created_at BETWEEN :start AND :end " +
           "AND e.metadata->>'duration' IS NOT NULL", nativeQuery = true)
    long countSessionsWithDurationByCreatorAndPeriod(@Param("creator") String creatorId, @Param("start") Instant start, @Param("end") Instant end);

    @Query(value = "SELECT COUNT(e.id) FROM analytics_events e " +
           "WHERE e.event_type = 'CHAT_MESSAGE_SENT' " +
           "AND e.metadata->>'creator' = :creatorId " +
           "AND e.created_at BETWEEN :start AND :end", nativeQuery = true)
    long countChatMessagesByCreatorAndPeriod(@Param("creator") String creatorId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COUNT(e) FROM AnalyticsEvent e WHERE e.user.id = :userId AND e.eventType = :eventType AND e.createdAt >= :since")
    long countByUserIdAndEventTypeAndCreatedAtAfter(@Param("userId") Long userId, @Param("eventType") AnalyticsEventType eventType, @Param("since") Instant since);

    @Query("SELECT e FROM AnalyticsEvent e WHERE e.user.id = :userId AND e.eventType = :eventType ORDER BY e.createdAt DESC LIMIT 1")
    java.util.Optional<AnalyticsEvent> findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(@Param("userId") Long userId, @Param("eventType") AnalyticsEventType eventType);

    @Query(value = "SELECT DISTINCT e.metadata->>'ip' FROM analytics_events e " +
           "WHERE e.user_id = :userId " +
           "AND e.created_at >= :since " +
           "AND e.metadata->>'ip' IS NOT NULL", nativeQuery = true)
    List<String> findDistinctIpsByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("since") Instant since);

    org.springframework.data.domain.Page<AnalyticsEvent> findAllByEventType(AnalyticsEventType eventType, org.springframework.data.domain.Pageable pageable);
}
