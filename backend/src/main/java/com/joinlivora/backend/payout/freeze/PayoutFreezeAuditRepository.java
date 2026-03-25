package com.joinlivora.backend.payout.freeze;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for PayoutFreezeAuditLog entity.
 */
@Repository
public interface PayoutFreezeAuditRepository extends JpaRepository<PayoutFreezeAuditLog, Long> {

    List<PayoutFreezeAuditLog> findByCreatorIdOrderByCreatedAtDesc(Long creatorId);

    List<PayoutFreezeAuditLog> findByCreatorIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long creatorId, Instant from, Instant to);

    List<PayoutFreezeAuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);

    List<PayoutFreezeAuditLog> findAllByOrderByCreatedAtDesc();

    Page<PayoutFreezeAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByCreatedAtAfter(Instant from);

    default long countEventsLast24h() {
        return countByCreatedAtAfter(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));
    }

    @Query("""
        SELECT DATE(a.createdAt),
               COUNT(a)
        FROM PayoutFreezeAuditLog a
        WHERE a.action = 'FREEZE'
          AND a.createdAt >= :from
        GROUP BY DATE(a.createdAt)
        ORDER BY DATE(a.createdAt)
    """)
    List<Object[]> countFreezesGroupedByDay(Instant from);
}
