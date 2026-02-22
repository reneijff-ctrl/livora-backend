package com.joinlivora.backend.monetization;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TipRepository extends JpaRepository<Tip, UUID> {
    Optional<Tip> findByStripePaymentIntentId(String stripePaymentIntentId);
    Optional<Tip> findByClientRequestId(String clientRequestId);
    boolean existsByClientRequestId(String clientRequestId);
    long countBySenderUserIdAndCreatedAtAfter(User sender, Instant since);
    long countBySenderUserId_IdAndCreatedAtAfter(Long userId, Instant since);
    long countByCreatorUserIdAndStatusAndCreatedAtAfter(User creator, TipStatus status, Instant since);

    @Query("SELECT SUM(t.amount) FROM Tip t WHERE t.creatorUserId = :creator AND t.status = :status AND t.createdAt >= :since")
    BigDecimal sumAmountByCreatorUserIdAndStatusAndCreatedAtAfter(@Param("creator") User creator, @Param("status") TipStatus status, @Param("since") Instant since);

    @Query("SELECT SUM(t.amount) FROM Tip t WHERE t.status = com.joinlivora.backend.monetization.TipStatus.COMPLETED")
    BigDecimal sumAllPaidTips();

    @Query("SELECT t FROM Tip t JOIN FETCH t.senderUserId JOIN FETCH t.creatorUserId ORDER BY t.createdAt DESC")
    List<Tip> findAllWithUsers();

    boolean existsByCreatorUserIdAndStatusAndAmountGreaterThan(User creator, TipStatus status, BigDecimal amount);

    @Query("SELECT t.senderUserId.id, t.creatorUserId.id, SUM(t.amount), COUNT(t), MIN(t.createdAt), MAX(t.createdAt) " +
           "FROM Tip t " +
           "WHERE t.createdAt >= :since AND t.status = com.joinlivora.backend.monetization.TipStatus.COMPLETED " +
           "GROUP BY t.senderUserId.id, t.creatorUserId.id")
    List<Object[]> aggregateTips(@Param("since") Instant since);
}
