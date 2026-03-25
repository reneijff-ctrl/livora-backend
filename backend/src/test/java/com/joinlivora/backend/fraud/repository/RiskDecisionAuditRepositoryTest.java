package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.RiskDecisionAudit;
import com.joinlivora.backend.fraud.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RiskDecisionAuditRepositoryTest {

    @Autowired
    private RiskDecisionAuditRepository repository;

    @Test
    void shouldPersistAndRetrieveAudit() {
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        RiskDecisionAudit audit = RiskDecisionAudit.builder()
                .userId(userId)
                .transactionId(transactionId)
                .decisionType("FRAUD_CHECK")
                .riskLevel(RiskLevel.HIGH)
                .score(85)
                .triggeredBy("SYSTEM")
                .actionsTaken("FLAG_FOR_REVIEW")
                .reason("High velocity detected")
                .build();

        RiskDecisionAudit saved = repository.save(audit);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        List<RiskDecisionAudit> byUser = repository.findAllByUserIdOrderByCreatedAtDesc(userId);
        assertThat(byUser).hasSize(1);
        assertThat(byUser.get(0).getRiskLevel()).isEqualTo(RiskLevel.HIGH);

        List<RiskDecisionAudit> byTransaction = repository.findAllByTransactionIdOrderByCreatedAtDesc(transactionId);
        assertThat(byTransaction).hasSize(1);
        assertThat(byTransaction.get(0).getTransactionId()).isEqualTo(transactionId);
    }
}








