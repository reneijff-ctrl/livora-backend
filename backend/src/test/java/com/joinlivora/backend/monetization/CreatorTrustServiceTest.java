package com.joinlivora.backend.monetization;

import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.FraudSource;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.monetization.dto.CollusionResult;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorTrustServiceTest {

    @Mock
    private CollusionDetectionService collusionDetectionService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FraudDetectionService fraudDetectionService;
    @Mock
    private CreatorCollusionRecordRepository creatorCollusionRecordRepository;
    @Mock
    private CollusionAuditService collusionAuditService;
    @Mock
    private com.joinlivora.backend.reputation.service.ReputationEventService reputationEventService;
    @Mock
    private com.joinlivora.backend.fraud.service.RiskDecisionEngine riskDecisionEngine;

    @InjectMocks
    private CreatorTrustService creatorTrustService;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@test.com");
        creator.setStatus(UserStatus.ACTIVE);
        creator.setTrustScore(100);

        lenient().when(riskDecisionEngine.evaluate(any(), any(), anyInt(), any()))
                .thenReturn(com.joinlivora.backend.fraud.dto.RiskDecisionResult.builder()
                        .decision(com.joinlivora.backend.fraud.model.RiskDecision.ALLOW)
                        .explanationId(java.util.UUID.randomUUID())
                        .riskScore(0)
                        .build());
    }

    @Test
    void evaluateTrust_LowScore_ShouldDoNothing() {
        CollusionResult low = new CollusionResult(30, List.of());
        when(collusionDetectionService.detectCollusion(any())).thenReturn(low);

        creatorTrustService.evaluateTrust(creator);

        verify(collusionAuditService).audit(creator, low);
        verify(creatorCollusionRecordRepository).save(any());
        verify(userRepository, never()).save(any());
        verify(fraudDetectionService, never()).logFraudSignal(anyLong(), any(), any(), any(), anyString());
        verify(collusionAuditService, never()).recordRestriction(any(), anyString(), anyInt());
    }

    @Test
    void evaluateTrust_ReduceTrustRule_ShouldDecrementScore() {
        CollusionResult medium = new CollusionResult(65, List.of("REPEATED_TIPPING"));
        when(collusionDetectionService.detectCollusion(any())).thenReturn(medium);

        creatorTrustService.evaluateTrust(creator);

        assertThat(creator.getTrustScore()).isEqualTo(80);
        verify(userRepository).save(creator);
        verify(collusionAuditService).audit(creator, medium);
        verify(collusionAuditService).recordRestriction(creator, "TRUST_REDUCTION", 65);
        verify(fraudDetectionService).logFraudSignal(
                eq(1L), eq(FraudDecisionLevel.MEDIUM), eq(FraudSource.SYSTEM),
                eq(FraudSignalType.COLLUSION_DETECTED), contains("65")
        );
    }

    @Test
    void evaluateTrust_RestrictPayoutsRule_ShouldUpdateStatus() {
        CollusionResult high = new CollusionResult(85, List.of("CIRCULAR_TIPPING"));
        when(collusionDetectionService.detectCollusion(any())).thenReturn(high);

        creatorTrustService.evaluateTrust(creator);

        assertThat(creator.getStatus()).isEqualTo(UserStatus.PAYOUTS_FROZEN);
        verify(userRepository).save(creator);
        verify(collusionAuditService).audit(creator, high);
        verify(collusionAuditService).recordRestriction(creator, "PAYOUTS_FROZEN", 85);
        verify(fraudDetectionService).logFraudSignal(
                eq(1L), eq(FraudDecisionLevel.HIGH), eq(FraudSource.SYSTEM),
                eq(FraudSignalType.COLLUSION_DETECTED), contains("85")
        );
    }

    @Test
    void evaluateTrust_ManualReviewRule_ShouldUpdateStatus() {
        CollusionResult extreme = new CollusionResult(98, List.of("CLUSTER_FUNDING", "CIRCULAR_TIPPING"));
        when(collusionDetectionService.detectCollusion(any())).thenReturn(extreme);

        creatorTrustService.evaluateTrust(creator);

        assertThat(creator.getStatus()).isEqualTo(UserStatus.MANUAL_REVIEW);
        verify(userRepository).save(creator);
        verify(collusionAuditService).audit(creator, extreme);
        verify(collusionAuditService).recordRestriction(creator, "MANUAL_REVIEW", 98);
        verify(fraudDetectionService).logFraudSignal(
                eq(1L), eq(FraudDecisionLevel.HIGH), eq(FraudSource.SYSTEM),
                eq(FraudSignalType.COLLUSION_DETECTED), contains("98")
        );
    }
    
    @Test
    void evaluateTrust_StatusHierarchy_ShouldNotDowngrade() {
        creator.setStatus(UserStatus.SUSPENDED);
        CollusionResult high = new CollusionResult(85, List.of("CIRCULAR_TIPPING"));
        when(collusionDetectionService.detectCollusion(any())).thenReturn(high);

        creatorTrustService.evaluateTrust(creator);

        assertThat(creator.getStatus()).isEqualTo(UserStatus.SUSPENDED); // Should remain SUSPENDED
        verify(userRepository, never()).save(creator);
    }
}








