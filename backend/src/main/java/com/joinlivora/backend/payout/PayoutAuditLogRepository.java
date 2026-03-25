package com.joinlivora.backend.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PayoutAuditLogRepository extends JpaRepository<PayoutAuditLog, UUID> {
    List<PayoutAuditLog> findAllByPayoutIdOrderByCreatedAtDesc(UUID payoutId);
}
