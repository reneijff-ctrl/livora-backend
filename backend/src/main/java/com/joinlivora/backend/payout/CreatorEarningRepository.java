package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface CreatorEarningRepository extends JpaRepository<CreatorEarning, UUID> {
    List<CreatorEarning> findAllByCreatorOrderByCreatedAtDesc(User creator);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.createdAt >= :since")
    BigDecimal sumNetEarningsByCreatorAndSince(@Param("creator") User creator, @Param("since") Instant since);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.currency = 'TOKEN' AND e.createdAt >= :since")
    BigDecimal sumNetTokensByCreatorAndSince(@Param("creator") User creator, @Param("since") Instant since);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.currency <> 'TOKEN' AND e.createdAt >= :since")
    BigDecimal sumNetRevenueByCreatorAndSince(@Param("creator") User creator, @Param("since") Instant since);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.currency = 'TOKEN' AND e.locked = false")
    BigDecimal sumTotalNetTokensByCreator(@Param("creator") User creator);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.currency <> 'TOKEN' AND e.locked = false")
    BigDecimal sumTotalNetRevenueByCreator(@Param("creator") User creator);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator")
    BigDecimal sumTotalNetEarningsByCreator(@Param("creator") User creator);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.sourceType = :source")
    BigDecimal sumNetEarningsByCreatorAndSource(@Param("creator") User creator, @Param("source") EarningSource source);

    @Query("SELECT COUNT(e) FROM CreatorEarning e WHERE e.creator = :creator AND e.sourceType = :sourceType")
    long countByCreatorAndSource(@Param("creator") User creator, @Param("sourceType") EarningSource sourceType);

    List<CreatorEarning> findByInvoiceIsNullAndCreatedAtBetween(Instant start, Instant end);

    @Query("SELECT DISTINCT e.creator FROM CreatorEarning e WHERE e.invoice IS NULL AND e.createdAt BETWEEN :start AND :end")
    List<User> findCreatorsWithUninvoicedEarnings(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT DISTINCT e.currency FROM CreatorEarning e WHERE e.creator = :creator AND e.invoice IS NULL AND e.createdAt BETWEEN :start AND :end")
    List<String> findCurrenciesByCreatorWithUninvoicedEarnings(@Param("creator") User creator, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT e FROM CreatorEarning e WHERE e.creator = :creator AND e.currency = :currency AND e.invoice IS NULL AND e.createdAt BETWEEN :start AND :end")
    List<CreatorEarning> findUninvoicedEarningsByCreatorCurrencyAndPeriod(@Param("creator") User creator, @Param("currency") String currency, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT DISTINCT e.creator FROM CreatorEarning e WHERE e.createdAt BETWEEN :start AND :end")
    List<User> findCreatorsWithEarningsInPeriod(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.createdAt BETWEEN :start AND :end")
    BigDecimal sumNetEarningsByCreatorAndPeriod(@Param("creator") User creator, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.sourceType = :sourceType AND e.createdAt BETWEEN :start AND :end")
    BigDecimal sumNetEarningsByCreatorAndSourceAndPeriod(@Param("creator") User creator, @Param("sourceType") EarningSource sourceType, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT e.sourceType, e.currency, SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.createdAt BETWEEN :start AND :end GROUP BY e.sourceType, e.currency")
    List<Object[]> sumNetEarningsByCreatorAndPeriodGroupedBySourceAndCurrency(@Param("creator") User creator, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.currency = 'TOKEN' AND e.createdAt BETWEEN :start AND :end")
    BigDecimal sumNetTokensByCreatorAndPeriod(@Param("creator") User creator, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.currency <> 'TOKEN' AND e.createdAt BETWEEN :start AND :end")
    BigDecimal sumNetRevenueByCreatorAndPeriod(@Param("creator") User creator, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COUNT(e) FROM CreatorEarning e WHERE e.creator = :creator AND e.sourceType = :sourceType AND e.createdAt BETWEEN :start AND :end")
    long countByCreatorAndSourceAndPeriod(@Param("creator") User creator, @Param("sourceType") EarningSource sourceType, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.sourceType IN :sources")
    BigDecimal sumNetEarningsByCreatorAndSources(@Param("creator") User creator, @Param("sources") java.util.Collection<EarningSource> sources);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.sourceType IN :sources AND e.createdAt >= :since")
    BigDecimal sumNetEarningsByCreatorAndSourcesSince(@Param("creator") User creator, @Param("sources") java.util.Collection<EarningSource> sources, @Param("since") Instant since);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.locked = true")
    BigDecimal sumPendingEarningsByCreator(@Param("creator") User creator);

    @Query("SELECT SUM(e.platformFee) FROM CreatorEarning e WHERE e.creator = :creator")
    BigDecimal sumTotalFeesByCreator(@Param("creator") User creator);

    @Query("SELECT SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator = :creator AND e.locked = false AND e.payout IS NULL")
    BigDecimal sumAvailableEarningsByCreator(@Param("creator") User creator);

    @Query("""
            SELECT COALESCE(SUM(e.netAmount), 0)
            FROM CreatorEarning e
            WHERE e.creator.id = :creatorId
            AND e.currency = 'TOKEN'
            AND e.locked = false
            AND e.dryRun = false
            """)
    BigDecimal sumAvailableTokens(@Param("creatorId") Long creatorId);

    @Query("""
            SELECT COALESCE(SUM(COALESCE(e.netAmountEur, 0)), 0)
            FROM CreatorEarning e
            WHERE e.creator.id = :creatorId
            AND e.locked = false
            AND e.dryRun = false
            """)
    BigDecimal sumAvailableEur(@Param("creatorId") Long creatorId);

    @Query("""
            SELECT DISTINCT e.creator.id
            FROM CreatorEarning e
            WHERE e.createdAt >= :since
            """)
    List<Long> findRecentlyActiveCreatorIds(@Param("since") Instant since);

    Optional<CreatorEarning> findByStripeChargeId(String stripeChargeId);

    List<CreatorEarning> findAllByPayout(CreatorPayout payout);

    List<CreatorEarning> findAllByPayoutRequest(PayoutRequest payoutRequest);
    
    long countByLockedTrueAndPayoutIsNullAndPayoutRequestIsNull();

    List<CreatorEarning> findAllByUserAndCurrencyOrderByCreatedAtAsc(User user, String currency);

    @Query("SELECT e FROM CreatorEarning e WHERE e.creator = :creator AND e.locked = false AND (e.payoutHold IS NULL OR e.payoutHold.status = 'RELEASED')")
    List<CreatorEarning> findAvailableEarningsByCreator(@Param("creator") User creator);

    @Query("SELECT e FROM CreatorEarning e JOIN FETCH e.creator " +
           "LEFT JOIN FETCH e.holdPolicy LEFT JOIN FETCH e.payoutHold " +
           "WHERE e.locked = true AND e.dryRun = false AND e.payout IS NULL AND (" +
           "(e.holdPolicy IS NOT NULL AND e.holdPolicy.expiresAt <= :now) OR " +
           "(e.payoutHold IS NOT NULL AND (" +
           "  e.payoutHold.status = 'RELEASED' OR " +
           "  (e.payoutHold.status = 'ACTIVE' AND e.payoutHold.holdUntil <= :now)" +
           ")) OR " +
           "(e.holdPolicy IS NULL AND e.payoutHold IS NULL))")
    List<CreatorEarning> findExpiredLockedEarnings(@Param("now") Instant now);

    @Query("SELECT e.creator.id, SUM(e.netAmount) FROM CreatorEarning e WHERE e.creator IN :creators GROUP BY e.creator.id")
    List<Object[]> sumTotalEarningsForCreators(@Param("creators") java.util.Collection<User> creators);

    @Query(value = "SELECT CAST(e.created_at AS DATE) AS earning_date, SUM(e.net_amount), COUNT(DISTINCT e.user_id) " +
           "FROM creator_earnings_history e WHERE e.creator_id = :creatorId AND e.created_at BETWEEN :start AND :end " +
           "GROUP BY CAST(e.created_at AS DATE) ORDER BY earning_date ASC",
           nativeQuery = true)
    List<Object[]> findDailyEarningsSummaryByCreatorAndPeriod(
            @Param("creatorId") Long creatorId,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
