package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.model.RiskAction;
import com.joinlivora.backend.fraud.model.RiskFactor;
import com.joinlivora.backend.fraud.model.RiskScore;
import com.joinlivora.backend.fraud.repository.RiskScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskScoringServiceTest {

    @Mock
    private RiskScoreRepository riskScoreRepository;

    @InjectMocks
    private RiskScoringService riskScoringService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void calculateTotalScore_ShouldSumWeightsAndCapAt100() {
        Map<RiskFactor, Integer> factors = new HashMap<>();
        factors.put(RiskFactor.CHARGEBACK_RATE, 1); // 40
        factors.put(RiskFactor.CHARGEBACK_COUNT, 1); // 25
        factors.put(RiskFactor.HIGH_TIP_FREQUENCY, 1); // 15
        
        int score = riskScoringService.calculateTotalScore(factors);
        assertThat(score).isEqualTo(80);

        factors.put(RiskFactor.MANUAL_FLAG, 1); // +50 = 130 -> capped at 100
        score = riskScoringService.calculateTotalScore(factors);
        assertThat(score).isEqualTo(100);
    }

    @Test
    void calculateTotalScore_EmptyFactors_ShouldReturnZero() {
        int score = riskScoringService.calculateTotalScore(new HashMap<>());
        assertThat(score).isEqualTo(0);
        
        score = riskScoringService.calculateTotalScore(null);
        assertThat(score).isEqualTo(0);
    }

    @Test
    void calculateAndPersist_ShouldSaveRiskScoreWithCorrectBreakdown() {
        Map<RiskFactor, Integer> factors = new HashMap<>();
        factors.put(RiskFactor.MULTIPLE_ACCOUNTS, 1);
        factors.put(RiskFactor.RAPID_PAYOUT_REQUESTS, 2);

        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(i -> i.getArguments()[0]);

        RiskScore result = riskScoringService.calculateAndPersist(userId, factors);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getScore()).isEqualTo(10 + 10 * 2); // 30
        assertThat(result.getBreakdown()).contains("MULTIPLE_ACCOUNTS(1)");
        assertThat(result.getBreakdown()).contains("RAPID_PAYOUT_REQUESTS(2)");
        assertThat(result.getLastEvaluatedAt()).isNotNull();

        ArgumentCaptor<RiskScore> captor = ArgumentCaptor.forClass(RiskScore.class);
        verify(riskScoreRepository).save(captor.capture());
        assertThat(captor.getValue().getScore()).isEqualTo(30);
    }

    @Test
    void evaluateAction_ShouldReturnCorrectActionForThresholds() {
        assertThat(riskScoringService.evaluateAction(85)).isEqualTo(RiskAction.ACCOUNT_TERMINATED);
        assertThat(riskScoringService.evaluateAction(80)).isEqualTo(RiskAction.ACCOUNT_TERMINATED);
        assertThat(riskScoringService.evaluateAction(79)).isEqualTo(RiskAction.ACCOUNT_SUSPENDED);
        assertThat(riskScoringService.evaluateAction(60)).isEqualTo(RiskAction.ACCOUNT_SUSPENDED);
        assertThat(riskScoringService.evaluateAction(59)).isEqualTo(RiskAction.PAYOUT_FROZEN);
        assertThat(riskScoringService.evaluateAction(40)).isEqualTo(RiskAction.PAYOUT_FROZEN);
        assertThat(riskScoringService.evaluateAction(39)).isEqualTo(RiskAction.NO_ACTION);
        assertThat(riskScoringService.evaluateAction(0)).isEqualTo(RiskAction.NO_ACTION);
    }
}








