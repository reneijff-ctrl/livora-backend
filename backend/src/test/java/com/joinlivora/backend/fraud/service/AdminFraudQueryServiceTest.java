package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.dto.FraudDashboardMetricsDTO;
import com.joinlivora.backend.fraud.model.*;
import com.joinlivora.backend.fraud.repository.FraudEventRepository;
import com.joinlivora.backend.fraud.repository.FraudScoreRepository;
import com.joinlivora.backend.fraud.repository.RiskScoreRepository;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFraudQueryServiceTest {

    @Mock
    private FraudEventRepository fraudEventRepository;

    @Mock
    private FraudScoreRepository fraudScoreRepository;

    @Mock
    private RiskScoreRepository riskScoreRepository;

    @Mock
    private RuleFraudSignalRepository ruleFraudSignalRepository;

    @InjectMocks
    private AdminFraudQueryService adminFraudQueryService;

    @Test
    void getFraudHistory_ShouldReturnEvents() {
        UUID userId = UUID.randomUUID();
        FraudEvent event = FraudEvent.builder().userId(userId).reason("Test").build();
        when(fraudEventRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(event));

        List<FraudEvent> result = adminFraudQueryService.getFraudHistory(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void getUsersWithEnforcement_ShouldReturnScores() {
        FraudScore score = FraudScore.builder()
                .userId(1L)
                .riskLevel("HIGH")
                .build();
        when(fraudScoreRepository.findAllByRiskLevelNot("LOW")).thenReturn(List.of(score));

        List<FraudScore> result = adminFraudQueryService.getUsersWithEnforcement();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    void getRiskScore_ShouldReturnScore() {
        UUID userId = UUID.randomUUID();
        RiskScore riskScore = RiskScore.builder().userId(userId).score(50).build();
        when(riskScoreRepository.findByUserId(userId)).thenReturn(Optional.of(riskScore));

        Optional<RiskScore> result = adminFraudQueryService.getRiskScore(userId);

        assertThat(result).isPresent();
        assertThat(result.get().getScore()).isEqualTo(50);
    }

    @Test
    void getFraudDashboardMetrics_ShouldAggregateCorrectly() {
        // Arrange
        when(ruleFraudSignalRepository.countByResolvedFalse()).thenReturn(15L);
        when(ruleFraudSignalRepository.countUnresolvedByRiskLevel()).thenReturn(Map.of(
                RiskLevel.CRITICAL, 3L,
                RiskLevel.HIGH, 7L,
                RiskLevel.MEDIUM, 5L
        ));
        when(fraudEventRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(4L);
        when(ruleFraudSignalRepository.countByTypeAndCreatedAtAfter(eq(FraudSignalType.NEW_ACCOUNT_TIPPING_HIGH), any(Instant.class))).thenReturn(3L);
        when(ruleFraudSignalRepository.countByTypeAndCreatedAtAfter(eq(FraudSignalType.NEW_ACCOUNT_TIPPING_MEDIUM), any(Instant.class))).thenReturn(2L);
        when(ruleFraudSignalRepository.countByTypeAndCreatedAtAfter(eq(FraudSignalType.NEW_ACCOUNT_TIP_CLUSTER), any(Instant.class))).thenReturn(1L);
        when(ruleFraudSignalRepository.countByTypeAndCreatedAtAfter(eq(FraudSignalType.RAPID_TIP_REPEATS), any(Instant.class))).thenReturn(5L);

        // Act
        FraudDashboardMetricsDTO metrics = adminFraudQueryService.getFraudDashboardMetrics();

        // Assert
        assertThat(metrics.getUnresolvedSignals()).isEqualTo(15L);
        assertThat(metrics.getCriticalSignals()).isEqualTo(3L);
        assertThat(metrics.getHighSignals()).isEqualTo(7L);
        assertThat(metrics.getEnforcementLast24h()).isEqualTo(4L);
        assertThat(metrics.getNewAccountTippingHigh()).isEqualTo(3L);
        assertThat(metrics.getNewAccountTippingMedium()).isEqualTo(2L);
        assertThat(metrics.getNewAccountTipCluster()).isEqualTo(1L);
        assertThat(metrics.getRapidTipRepeats()).isEqualTo(5L);
    }
}








