package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {
    List<Payout> findAllByUserOrderByCreatedAtDesc(User user);
    List<Payout> findAllByStatus(PayoutStatus status);
}
