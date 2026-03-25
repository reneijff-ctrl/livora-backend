package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.RiskProfile;
import com.joinlivora.backend.monetization.CollusionDetectionService;
import com.joinlivora.backend.monetization.CreatorTrustService;
import com.joinlivora.backend.monetization.dto.CollusionResult;
import com.joinlivora.backend.payment.ChargebackHistoryService;
import com.joinlivora.backend.reputation.model.CreatorReputationSnapshot;
import com.joinlivora.backend.reputation.model.ReputationStatus;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskProfileServiceTest {

    @Mock
    private FraudDetectionService fraudDetectionService;
    @Mock
    private CollusionDetectionService collusionDetectionService;
    @Mock
    private ChargebackHistoryService chargebackHistoryService;
    @Mock
    private CreatorTrustService creatorTrustService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorReputationSnapshotRepository reputationSnapshotRepository;

    @InjectMocks
    private RiskProfileService riskProfileService;

    private UUID userId = new UUID(0L, 100L);
    private Long userIdLong = 100L;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(userIdLong);
        user.setTrustScore(85);
    }

    @Test
    void testGenerateRiskProfile_Success() {
        when(userRepository.findById(userIdLong)).thenReturn(Optional.of(user));
        when(fraudDetectionService.getHighestSignalLevel(eq(userIdLong), any(Instant.class))).thenReturn(FraudDecisionLevel.LOW);
        when(collusionDetectionService.detectCollusion(userId)).thenReturn(CollusionResult.builder().collusionScore(30).build());
        when(chargebackHistoryService.getChargebackRiskScore(userIdLong)).thenReturn(20);
        when(reputationSnapshotRepository.findById(userId)).thenReturn(Optional.empty());

        RiskProfile profile = riskProfileService.generateRiskProfile(userId);

        assertNotNull(profile);
        assertEquals(userId, profile.getUserId());
        assertEquals(30, profile.getRiskScore()); // max(10, 30, 20)
        assertEquals(85, profile.getTrustScore());
        verify(creatorTrustService).evaluateTrust(user);
    }

    @Test
    void testGenerateRiskProfile_TrustedReputation() {
        when(userRepository.findById(userIdLong)).thenReturn(Optional.of(user));
        when(fraudDetectionService.getHighestSignalLevel(eq(userIdLong), any(Instant.class))).thenReturn(FraudDecisionLevel.LOW);
        when(collusionDetectionService.detectCollusion(userId)).thenReturn(CollusionResult.builder().collusionScore(30).build());
        when(chargebackHistoryService.getChargebackRiskScore(userIdLong)).thenReturn(20);
        
        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(userId)
                .status(ReputationStatus.TRUSTED)
                .build();
        when(reputationSnapshotRepository.findById(userId)).thenReturn(Optional.of(snapshot));

        RiskProfile profile = riskProfileService.generateRiskProfile(userId);

        assertEquals(10, profile.getRiskScore()); // max(10, 30, 20) = 30; 30 - 20 = 10
    }

    @Test
    void testGenerateRiskProfile_WatchedReputation() {
        when(userRepository.findById(userIdLong)).thenReturn(Optional.of(user));
        when(fraudDetectionService.getHighestSignalLevel(eq(userIdLong), any(Instant.class))).thenReturn(FraudDecisionLevel.LOW);
        when(collusionDetectionService.detectCollusion(userId)).thenReturn(CollusionResult.builder().collusionScore(30).build());
        when(chargebackHistoryService.getChargebackRiskScore(userIdLong)).thenReturn(20);

        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(userId)
                .status(ReputationStatus.WATCHED)
                .build();
        when(reputationSnapshotRepository.findById(userId)).thenReturn(Optional.of(snapshot));

        RiskProfile profile = riskProfileService.generateRiskProfile(userId);

        assertEquals(40, profile.getRiskScore()); // 30 + 10 = 40
    }

    @Test
    void testGenerateRiskProfile_RestrictedReputation() {
        when(userRepository.findById(userIdLong)).thenReturn(Optional.of(user));
        when(fraudDetectionService.getHighestSignalLevel(eq(userIdLong), any(Instant.class))).thenReturn(FraudDecisionLevel.LOW);
        when(collusionDetectionService.detectCollusion(userId)).thenReturn(CollusionResult.builder().collusionScore(30).build());
        when(chargebackHistoryService.getChargebackRiskScore(userIdLong)).thenReturn(20);

        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(userId)
                .status(ReputationStatus.RESTRICTED)
                .build();
        when(reputationSnapshotRepository.findById(userId)).thenReturn(Optional.of(snapshot));

        RiskProfile profile = riskProfileService.generateRiskProfile(userId);

        assertEquals(55, profile.getRiskScore()); // 30 + 25 = 55
    }

    @Test
    void testGenerateRiskProfile_ClampingScore() {
        when(userRepository.findById(userIdLong)).thenReturn(Optional.of(user));
        when(fraudDetectionService.getHighestSignalLevel(eq(userIdLong), any(Instant.class))).thenReturn(FraudDecisionLevel.HIGH);
        when(collusionDetectionService.detectCollusion(userId)).thenReturn(CollusionResult.builder().collusionScore(90).build());
        when(chargebackHistoryService.getChargebackRiskScore(userIdLong)).thenReturn(80);

        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(userId)
                .status(ReputationStatus.RESTRICTED)
                .build();
        when(reputationSnapshotRepository.findById(userId)).thenReturn(Optional.of(snapshot));

        RiskProfile profile = riskProfileService.generateRiskProfile(userId);

        assertEquals(100, profile.getRiskScore()); // max(100, 90, 80) = 100; 100 + 25 = 125 -> clamped to 100
    }

    @Test
    void testGenerateRiskProfile_ClampingScore_LowerBound() {
        when(userRepository.findById(userIdLong)).thenReturn(Optional.of(user));
        when(fraudDetectionService.getHighestSignalLevel(eq(userIdLong), any(Instant.class))).thenReturn(FraudDecisionLevel.LOW);
        when(collusionDetectionService.detectCollusion(userId)).thenReturn(CollusionResult.builder().collusionScore(10).build());
        when(chargebackHistoryService.getChargebackRiskScore(userIdLong)).thenReturn(5);

        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(userId)
                .status(ReputationStatus.TRUSTED)
                .build();
        when(reputationSnapshotRepository.findById(userId)).thenReturn(Optional.of(snapshot));

        RiskProfile profile = riskProfileService.generateRiskProfile(userId);

        assertEquals(0, profile.getRiskScore()); // max(10, 10, 5) = 10; 10 - 20 = -10 -> clamped to 0
    }

    @Test
    void testGenerateRiskProfile_HighFraudRisk() {
        when(userRepository.findById(userIdLong)).thenReturn(Optional.of(user));
        when(fraudDetectionService.getHighestSignalLevel(eq(userIdLong), any(Instant.class))).thenReturn(FraudDecisionLevel.HIGH);
        when(collusionDetectionService.detectCollusion(userId)).thenReturn(CollusionResult.builder().collusionScore(30).build());
        when(chargebackHistoryService.getChargebackRiskScore(userIdLong)).thenReturn(20);
        when(reputationSnapshotRepository.findById(userId)).thenReturn(Optional.empty());

        RiskProfile profile = riskProfileService.generateRiskProfile(userId);

        assertEquals(100, profile.getRiskScore()); // max(100, 30, 20)
    }

    @Test
    void testGenerateRiskProfile_UserNotFound() {
        when(userRepository.findById(userIdLong)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> riskProfileService.generateRiskProfile(userId));
    }
}








