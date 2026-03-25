package com.joinlivora.backend.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface CreatorStatsRepository extends JpaRepository<CreatorStats, UUID> {

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("UPDATE CreatorStats s SET s.totalNetEarnings = s.totalNetEarnings + :amount, s.todayNetEarnings = s.todayNetEarnings + :amount, s.updatedAt = :now WHERE s.creatorId = :creatorId")
    int incrementTotalNetEarnings(@Param("creatorId") UUID creatorId, @Param("amount") BigDecimal amount, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("UPDATE CreatorStats s SET s.totalNetTokens = s.totalNetTokens + :tokens, s.todayNetTokens = s.todayNetTokens + :tokens, s.updatedAt = :now WHERE s.creatorId = :creatorId")
    int incrementTotalNetTokens(@Param("creatorId") UUID creatorId, @Param("tokens") long tokens, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("UPDATE CreatorStats s SET s.subscriptionCount = s.subscriptionCount + :delta, s.updatedAt = :now WHERE s.creatorId = :creatorId")
    int incrementSubscriptionCount(@Param("creatorId") UUID creatorId, @Param("delta") long delta, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("UPDATE CreatorStats s SET s.tipsCount = s.tipsCount + :delta, s.updatedAt = :now WHERE s.creatorId = :creatorId")
    int incrementTipsCount(@Param("creatorId") UUID creatorId, @Param("delta") long delta, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("UPDATE CreatorStats s SET s.highlightsCount = s.highlightsCount + :delta, s.updatedAt = :now WHERE s.creatorId = :creatorId")
    int incrementHighlightsCount(@Param("creatorId") UUID creatorId, @Param("delta") long delta, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("UPDATE CreatorStats s SET s.todayNetEarnings = 0, s.todayNetTokens = 0, s.updatedAt = :now")
    void resetAllTodayStats(@Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("UPDATE CreatorStats s SET s.totalNetEarnings = :totalNet, s.subscriptionCount = :subCount, s.tipsCount = :tipCount, s.highlightsCount = :highlightCount, s.updatedAt = :now WHERE s.creatorId = :creatorId")
    void updateCreatorTotals(@Param("creatorId") UUID creatorId, @Param("totalNet") BigDecimal totalNet, @Param("subCount") long subCount, @Param("tipCount") long tipCount, @Param("highlightCount") long highlightCount, @Param("now") Instant now);
}
