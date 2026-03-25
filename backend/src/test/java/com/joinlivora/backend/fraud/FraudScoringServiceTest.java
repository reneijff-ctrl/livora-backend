package com.joinlivora.backend.fraud;

import com.joinlivora.backend.fraud.model.FraudDecision;
import com.joinlivora.backend.fraud.model.FraudRiskLevel;
import com.joinlivora.backend.fraud.model.FraudRiskResult;
import com.joinlivora.backend.fraud.model.FraudSignal;
import com.joinlivora.backend.fraud.repository.FraudDecisionRepository;
import com.joinlivora.backend.fraud.repository.FraudSignalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudScoringServiceTest {

    private FraudScoringService fraudRiskService;

    @Mock
    private FraudDecisionRepository fraudDecisionRepository;

    @Mock
    private FraudSignalRepository fraudSignalRepository;

    @Mock
    private com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository ruleFraudSignalRepository;

    @Mock
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;

    @Mock
    private com.joinlivora.backend.abuse.repository.AbuseEventRepository abuseEventRepository;

    @Mock
    private com.joinlivora.backend.user.UserService userService;

    @Mock
    private com.joinlivora.backend.admin.service.AdminRealtimeEventService adminRealtimeEventService;

    @BeforeEach
    void setUp() {
        fraudRiskService = new FraudScoringService(fraudDecisionRepository, fraudSignalRepository, ruleFraudSignalRepository, restrictionService, abuseEventRepository, userService, adminRealtimeEventService);
    }

    @Test
    void recordDecision_ShouldCallRestrictionService() {
        // Given
        UUID userId = UUID.randomUUID();
        FraudRiskResult result = new FraudRiskResult(FraudRiskLevel.MEDIUM, 50, List.of("Some risk"));
        
        // When
        fraudRiskService.recordDecision(userId, null, null, result);
        
        // Then
        verify(restrictionService).applyRestriction(eq(userId), eq(50), contains("Some risk"));
    }

    @Test
    void recordDecision_WhenEscalated_ShouldLogAbuseEvent() {
        // Given
        UUID userId = UUID.randomUUID();
        FraudRiskResult result = new FraudRiskResult(FraudRiskLevel.HIGH, 70, List.of("High risk"));
        
        when(restrictionService.applyRestriction(any(), anyInt(), anyString())).thenReturn(true);
        
        // When
        fraudRiskService.recordDecision(userId, null, null, result);
        
        // Then
        verify(abuseEventRepository).save(argThat(event -> 
                event.getUserId().equals(userId) &&
                event.getEventType() == com.joinlivora.backend.abuse.model.AbuseEventType.RESTRICTION_ESCALATED &&
                event.getDescription().contains("High risk")
        ));
    }

    @Test
    void recordDecision_ShouldSaveFraudSignal_WhenMediumRisk() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        FraudRiskResult result = new FraudRiskResult(FraudRiskLevel.MEDIUM, 50, List.of("Medium Risk"));

        // When
        fraudRiskService.recordDecision(userId, roomId, null, result);

        // Then
        ArgumentCaptor<FraudSignal> captor = ArgumentCaptor.forClass(FraudSignal.class);
        verify(fraudSignalRepository).save(captor.capture());
        FraudSignal saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(roomId, saved.getRoomId());
        assertEquals(50, saved.getScore());
        assertEquals(FraudRiskLevel.MEDIUM, saved.getRiskLevel());
    }

    @Test
    void recordDecision_ShouldNotSaveFraudSignal_WhenLowRisk() {
        // Given
        UUID userId = UUID.randomUUID();
        FraudRiskResult result = new FraudRiskResult(FraudRiskLevel.LOW, 10, List.of("Low Risk"));

        // When
        fraudRiskService.recordDecision(userId, null, null, result);

        // Then
        verify(fraudSignalRepository, never()).save(any());
    }

    @Test
    void calculateRisk_LowRiskScenario() {
        // Given: Tip 50, 1 tip, 30 days old, no chargebacks, no IP change
        FraudRiskResult result = fraudRiskService.calculateRisk(
                UUID.randomUUID(),
                new BigDecimal("50"),
                1,
                new BigDecimal("50"),
                30,
                false,
                0
        );

        // Then
        assertEquals(0, result.score());
        assertEquals(FraudRiskLevel.LOW, result.level());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void calculateRisk_MediumRiskScenario_HighVelocity() {
        // Given: 6 tips in 5 mins (+25), €210 in short window (+30) = 55 (MEDIUM)
        FraudRiskResult result = fraudRiskService.calculateRisk(
                UUID.randomUUID(),
                new BigDecimal("10"),
                6,
                new BigDecimal("210"),
                30,
                false,
                0
        );

        // Then
        assertEquals(55, result.score());
        assertEquals(FraudRiskLevel.MEDIUM, result.level());
        assertEquals(2, result.reasons().size());
    }

    @Test
    void recordDecision_ShouldSaveMediumRisk() {
        // Given
        UUID userId = UUID.randomUUID();
        Long tipId = 12345L;
        FraudRiskResult result = new FraudRiskResult(FraudRiskLevel.MEDIUM, 50, List.of("Reason 1", "Reason 2"));

        // When
        fraudRiskService.recordDecision(userId, null, tipId, result);

        // Then
        ArgumentCaptor<FraudDecision> captor = ArgumentCaptor.forClass(FraudDecision.class);
        verify(fraudDecisionRepository).save(captor.capture());
        FraudDecision saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(tipId, saved.getRelatedTipId());
        assertEquals(50, saved.getScore());
        assertEquals(FraudRiskLevel.MEDIUM, saved.getRiskLevel());
        assertEquals("Reason 1, Reason 2", saved.getReasons());
    }

    @Test
    void recordDecision_ShouldSaveHighRiskWithNullTipId() {
        // Given
        UUID userId = UUID.randomUUID();
        FraudRiskResult result = new FraudRiskResult(FraudRiskLevel.HIGH, 80, List.of("High Risk"));

        // When
        fraudRiskService.recordDecision(userId, null, null, result);

        // Then
        ArgumentCaptor<FraudDecision> captor = ArgumentCaptor.forClass(FraudDecision.class);
        verify(fraudDecisionRepository).save(captor.capture());
        FraudDecision saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertNull(saved.getRelatedTipId());
        assertEquals(80, saved.getScore());
        assertEquals(FraudRiskLevel.HIGH, saved.getRiskLevel());
    }

    @Test
    void recordDecision_ShouldSaveLowRiskWithReasons() {
        // Given
        FraudRiskResult result = new FraudRiskResult(FraudRiskLevel.LOW, 10, List.of("Low Risk Reason"));

        // When
        fraudRiskService.recordDecision(UUID.randomUUID(), null, null, result);

        // Then
        verify(fraudDecisionRepository).save(any());
    }

    @Test
    void recordDecision_ShouldNotSaveLowRiskWithoutReasons() {
        // Given
        FraudRiskResult result = new FraudRiskResult(FraudRiskLevel.LOW, 0, java.util.Collections.emptyList());

        // When
        fraudRiskService.recordDecision(UUID.randomUUID(), null, null, result);

        // Then
        verify(fraudDecisionRepository, never()).save(any());
    }

    @Test
    void calculateRisk_ShouldCapAt100() {
        // Given: Repeated chargebacks (+100)
        FraudRiskResult result = fraudRiskService.calculateRisk(
                UUID.randomUUID(),
                new BigDecimal("10"),
                1,
                new BigDecimal("10"),
                30,
                false,
                2
        );

        // Then
        assertEquals(100, result.score());
        assertEquals(FraudRiskLevel.CRITICAL, result.level());
    }

    @Test
    void calculateRisk_RepeatedChargebacks_Critical() {
        FraudRiskResult result = fraudRiskService.calculateRisk(
                UUID.randomUUID(),
                BigDecimal.TEN,
                0,
                BigDecimal.TEN,
                30,
                false,
                2
        );

        assertEquals(100, result.score());
        assertEquals(FraudRiskLevel.CRITICAL, result.level());
        assertTrue(result.reasons().get(0).contains("Repeated chargebacks detected"));
    }

    @Test
    void calculateRisk_IpOrDeviceChange() {
        FraudRiskResult result = fraudRiskService.calculateRisk(
                UUID.randomUUID(),
                BigDecimal.TEN,
                0,
                BigDecimal.TEN,
                30,
                true,
                0
        );

        assertEquals(30, result.score());
        assertEquals(FraudRiskLevel.LOW, result.level());
        assertTrue(result.reasons().contains("IP or device change detected"));
    }

    @Test
    void recordSignal_NewAccountHighRisk_ShouldUseHighRiskLevel() {
        // Given
        Long tipperId = 1L;
        Long creatorId = 2L;
        BigDecimal amount = BigDecimal.TEN;

        // When
        fraudRiskService.recordSignal(com.joinlivora.backend.fraud.model.FraudSignalType.NEW_ACCOUNT_TIPPING_HIGH, tipperId, creatorId, amount);

        // Then
        ArgumentCaptor<com.joinlivora.backend.fraud.model.RuleFraudSignal> signalCaptor = ArgumentCaptor.forClass(com.joinlivora.backend.fraud.model.RuleFraudSignal.class);
        verify(ruleFraudSignalRepository).save(signalCaptor.capture());
        assertEquals(com.joinlivora.backend.fraud.model.FraudDecisionLevel.HIGH, signalCaptor.getValue().getRiskLevel());
        
        // Verify tipper decision
        verify(fraudDecisionRepository).save(argThat(decision -> 
                decision.getUserId().equals(new java.util.UUID(0L, tipperId)) &&
                decision.getRiskLevel() == FraudRiskLevel.HIGH && 
                decision.getScore() == 75
        ));

        // Verify creator decision (NEW: +20 score)
        verify(fraudDecisionRepository).save(argThat(decision -> 
                decision.getUserId().equals(new java.util.UUID(0L, creatorId)) &&
                decision.getRiskLevel() == FraudRiskLevel.LOW && 
                decision.getScore() == 20 &&
                decision.getReasons().contains("COORDINATED_TIPPING_FARM_TARGET")
        ));
    }

    @Test
    void recordSignal_NewAccountMediumRisk_ShouldUseMediumRiskLevel() {
        // Given
        Long tipperId = 1L;
        Long creatorId = 2L;
        BigDecimal amount = BigDecimal.TEN;

        // When
        fraudRiskService.recordSignal(com.joinlivora.backend.fraud.model.FraudSignalType.NEW_ACCOUNT_TIPPING_MEDIUM, tipperId, creatorId, amount);

        // Then
        ArgumentCaptor<com.joinlivora.backend.fraud.model.RuleFraudSignal> signalCaptor = ArgumentCaptor.forClass(com.joinlivora.backend.fraud.model.RuleFraudSignal.class);
        verify(ruleFraudSignalRepository).save(signalCaptor.capture());
        assertEquals(com.joinlivora.backend.fraud.model.FraudDecisionLevel.MEDIUM, signalCaptor.getValue().getRiskLevel());
        
        verify(fraudDecisionRepository).save(argThat(decision -> 
                decision.getRiskLevel() == FraudRiskLevel.MEDIUM && decision.getScore() == 40
        ));
    }

    @Test
    void recordSignal_NewAccountTipCluster_ShouldIncreaseCreatorRiskScore() {
        // Given
        Long tipperId = 5L;
        Long creatorId = 1L;
        BigDecimal amount = BigDecimal.valueOf(100);

        // When
        fraudRiskService.recordSignal(com.joinlivora.backend.fraud.model.FraudSignalType.NEW_ACCOUNT_TIP_CLUSTER, tipperId, creatorId, amount);

        // Then
        // Verify creator rule-based signal
        ArgumentCaptor<com.joinlivora.backend.fraud.model.RuleFraudSignal> signalCaptor = ArgumentCaptor.forClass(com.joinlivora.backend.fraud.model.RuleFraudSignal.class);
        verify(ruleFraudSignalRepository).save(signalCaptor.capture());
        assertEquals(creatorId, signalCaptor.getValue().getUserId());
        assertEquals(com.joinlivora.backend.fraud.model.FraudSignalType.NEW_ACCOUNT_TIP_CLUSTER, signalCaptor.getValue().getType());
        assertEquals(com.joinlivora.backend.fraud.model.FraudDecisionLevel.HIGH, signalCaptor.getValue().getRiskLevel());

        // Verify creator decision increase (score 85, HIGH risk)
        verify(fraudDecisionRepository).save(argThat(decision -> 
                decision.getUserId().equals(new java.util.UUID(0L, creatorId)) &&
                decision.getScore() == 85 &&
                decision.getRiskLevel() == FraudRiskLevel.HIGH &&
                decision.getReasons().contains("NEW_ACCOUNT_TIP_CLUSTER")
        ));

        // Ensure no decision recorded for tipper for this specific signal
        verify(fraudDecisionRepository, never()).save(argThat(decision -> 
                decision.getUserId().equals(new java.util.UUID(0L, tipperId))
        ));
    }

    @Test
    void recordSignal_RapidTipRepeats_ShouldUseHighRiskLevel() {
        // Given
        Long tipperId = 1L;
        Long creatorId = 2L;
        BigDecimal amount = BigDecimal.TEN;

        // When
        fraudRiskService.recordSignal(com.joinlivora.backend.fraud.model.FraudSignalType.RAPID_TIP_REPEATS, tipperId, creatorId, amount);

        // Then
        ArgumentCaptor<com.joinlivora.backend.fraud.model.RuleFraudSignal> signalCaptor = ArgumentCaptor.forClass(com.joinlivora.backend.fraud.model.RuleFraudSignal.class);
        verify(ruleFraudSignalRepository).save(signalCaptor.capture());
        assertEquals(com.joinlivora.backend.fraud.model.FraudDecisionLevel.HIGH, signalCaptor.getValue().getRiskLevel());
        
        // Verify tipper decision
        verify(fraudDecisionRepository).save(argThat(decision -> 
                decision.getUserId().equals(new java.util.UUID(0L, tipperId)) &&
                decision.getRiskLevel() == FraudRiskLevel.HIGH && 
                decision.getScore() == 70
        ));
    }
}








