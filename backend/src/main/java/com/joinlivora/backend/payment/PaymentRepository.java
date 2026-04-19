package com.joinlivora.backend.payment;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findAllByUserOrderByCreatedAtDesc(User user);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.createdAt >= :since AND p.success = true")
    BigDecimal calculateRevenue(@Param("since") java.time.Instant since);

    default BigDecimal sumRevenueLast24h() {
        return calculateRevenue(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));
    }

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.createdAt BETWEEN :start AND :end AND p.success = true")
    BigDecimal calculateRevenueByPeriod(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    @Query("SELECT COUNT(DISTINCT p.user.id) FROM Payment p WHERE p.createdAt >= :since AND p.success = true")
    long countPayingUsers(@Param("since") Instant since);

    java.util.Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);
    java.util.Optional<Payment> findByStripeSessionId(String stripeSessionId);
    boolean existsByStripePaymentIntentId(String stripePaymentIntentId);
    boolean existsByStripeSessionId(String stripeSessionId);
    boolean existsByStripeInvoiceId(String stripeInvoiceId);

    long countByUserIdAndSuccessAndCreatedAtAfter(Long userId, boolean success, Instant after);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.user.id = :userId AND p.success = true AND p.createdAt >= :since")
    BigDecimal sumAmountByUserIdAndSuccessAndCreatedAtAfter(@Param("userId") Long userId, @Param("since") Instant since);

    @Query("SELECT p.country FROM Payment p WHERE p.user.id = :userId AND p.success = true ORDER BY p.createdAt DESC")
    List<String> findLastSuccessfulCountriesByUserId(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Payment> findAllByRiskLevelIn(java.util.Collection<com.joinlivora.backend.fraud.model.RiskLevel> riskLevels, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Payment> findAllBySuccessTrue(org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT CAST(p.createdAt AS date), SUM(p.amount) FROM Payment p " +
           "WHERE p.createdAt >= :after AND p.success = true GROUP BY CAST(p.createdAt AS date) ORDER BY CAST(p.createdAt AS date)")
    java.util.List<Object[]> calculateRevenueGroupedByDay(@Param("after") Instant after);
}
