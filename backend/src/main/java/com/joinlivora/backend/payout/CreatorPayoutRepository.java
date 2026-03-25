package com.joinlivora.backend.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CreatorPayoutRepository extends JpaRepository<CreatorPayout, UUID> {
    List<CreatorPayout> findAllByCreatorIdOrderByCreatedAtDesc(UUID creatorId);
    List<CreatorPayout> findAllByStatus(PayoutStatus status);
    long countByStatusAndCreatedAtBefore(PayoutStatus status, Instant dateTime);

    @Query("SELECT SUM(p.amount) FROM CreatorPayout p WHERE p.creatorId = :creatorId AND p.status = 'COMPLETED' AND p.createdAt >= :since")
    BigDecimal sumPaidAmountByCreatorIdAndCreatedAtAfter(UUID creatorId, Instant since);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM CreatorPayout p WHERE p.creatorId = :creatorId AND p.status = :status")
    BigDecimal sumAmountByCreatorIdAndStatus(@org.springframework.data.repository.query.Param("creatorId") UUID creatorId,
                                             @org.springframework.data.repository.query.Param("status") PayoutStatus status);
}
