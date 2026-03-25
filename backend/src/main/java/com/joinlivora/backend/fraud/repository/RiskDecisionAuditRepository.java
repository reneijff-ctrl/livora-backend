package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.RiskDecisionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RiskDecisionAuditRepository extends JpaRepository<RiskDecisionAudit, UUID> {
    List<RiskDecisionAudit> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<RiskDecisionAudit> findAllByTransactionIdOrderByCreatedAtDesc(UUID transactionId);
}
