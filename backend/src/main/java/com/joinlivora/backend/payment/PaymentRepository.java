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

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.createdAt >= :since")
    BigDecimal calculateRevenue(@Param("since") Instant since);

    @Query("SELECT COUNT(DISTINCT p.user.id) FROM Payment p WHERE p.createdAt >= :since")
    long countPayingUsers(@Param("since") Instant since);
}
