package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {
    List<Payout> findAllByUserOrderByCreatedAtDesc(User user);
    List<Payout> findAllByStatus(PayoutStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(p.eurAmount) FROM Payout p WHERE p.user = :user AND p.status = :status")
    java.math.BigDecimal sumEurAmountByUserAndStatus(@org.springframework.data.repository.query.Param("user") User user, @org.springframework.data.repository.query.Param("status") PayoutStatus status);
}
