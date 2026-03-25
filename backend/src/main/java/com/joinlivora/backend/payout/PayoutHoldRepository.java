package com.joinlivora.backend.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PayoutHoldRepository extends JpaRepository<PayoutHold, UUID> {
    List<PayoutHold> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<PayoutHold> findAllByStatusAndHoldUntilBefore(PayoutHoldStatus status, Instant now);
}
