package com.joinlivora.backend.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, UUID> {
    List<PayoutRequest> findAllByCreatorIdOrderByCreatedAtDesc(UUID creatorId);
    List<PayoutRequest> findAllByStatus(PayoutRequestStatus status);
    long countByStatus(PayoutRequestStatus status);
    long countByStatusAndCreatedAtBefore(PayoutRequestStatus status, java.time.Instant date);

    @Query("SELECT COALESCE(SUM(pr.amount), 0) FROM PayoutRequest pr")
    BigDecimal sumAllAmounts();

    @Query("SELECT COALESCE(SUM(pr.amount), 0) FROM PayoutRequest pr WHERE pr.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PayoutRequestStatus status);

    @Query("""
        SELECT COUNT(p), COALESCE(SUM(p.amount), 0)
        FROM PayoutRequest p
        WHERE p.status = 'PENDING'
    """)
    List<Object[]> getPendingPayoutMetrics();

    @Query("""
        SELECT DATE(p.createdAt) as day,
               COALESCE(SUM(p.amount), 0)
        FROM PayoutRequest p
        WHERE p.status = 'COMPLETED'
          AND p.createdAt >= :from
        GROUP BY DATE(p.createdAt)
        ORDER BY DATE(p.createdAt)
    """)
    List<Object[]> sumCompletedPayoutsGroupedByDay(Instant from);
}
