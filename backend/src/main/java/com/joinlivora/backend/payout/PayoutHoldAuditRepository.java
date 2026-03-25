package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.model.RiskSubjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayoutHoldAuditRepository extends JpaRepository<PayoutHoldAudit, UUID> {
    List<PayoutHoldAudit> findAllBySubjectIdAndSubjectTypeOrderByCreatedAtDesc(UUID subjectId, RiskSubjectType subjectType);
}
