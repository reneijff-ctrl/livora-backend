package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutFrequency;
import com.joinlivora.backend.payout.dto.PayoutLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PayoutPolicyAuditServiceTest {

    @Mock
    private PayoutPolicyDecisionRepository repository;

    @InjectMocks
    private PayoutPolicyAuditService auditService;

    @Test
    void logAutoDecision_ShouldSaveEntity() {
        UUID creatorId = UUID.randomUUID();
        UUID explanationId = UUID.randomUUID();
        PayoutLimit limit = PayoutLimit.builder()
                .maxPayoutAmount(new BigDecimal("100.00"))
                .payoutFrequency(PayoutFrequency.DAILY)
                .reason("Test type")
                .build();

        auditService.logAutoDecision(creatorId, 50, limit, explanationId);

        verify(repository).save(argThat(d -> 
                d.getCreatorId().equals(creatorId) &&
                d.getRiskScore() == 50 &&
                d.getAppliedLimitAmount().compareTo(new BigDecimal("100.00")) == 0 &&
                d.getAppliedLimitFrequency() == PayoutFrequency.DAILY &&
                d.getDecisionSource() == DecisionSource.AUTO &&
                d.getReason().equals("Test type") &&
                d.getExplanationId().equals(explanationId)
        ));
    }

    @Test
    void logAdminDecision_ShouldSaveEntity() {
        UUID creatorId = UUID.randomUUID();

        auditService.logAdminDecision(creatorId, new BigDecimal("500.00"), PayoutFrequency.WEEKLY, "Admin override");

        verify(repository).save(argThat(d -> 
                d.getCreatorId().equals(creatorId) &&
                d.getRiskScore() == null &&
                d.getAppliedLimitAmount().compareTo(new BigDecimal("500.00")) == 0 &&
                d.getAppliedLimitFrequency() == PayoutFrequency.WEEKLY &&
                d.getDecisionSource() == DecisionSource.ADMIN &&
                d.getReason().equals("Admin override")
        ));
    }
}








