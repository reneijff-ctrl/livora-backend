package com.joinlivora.backend.analytics.adaptive;

import com.joinlivora.backend.analytics.CreatorAdaptivePerformanceService;
import com.joinlivora.backend.analytics.adaptive.dto.TipRecommendationResponse;
import com.joinlivora.backend.analytics.dto.CreatorAdaptivePerformanceDTO;
import com.joinlivora.backend.payout.CreatorEarningRepository;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicTipRecommendationServiceTest {

    @Mock
    private CreatorAdaptivePerformanceService performanceService;

    @Mock
    private WhaleRiskModel whaleRiskModel;

    @Mock
    private CreatorEarningRepository creatorEarningRepository;

    @InjectMocks
    private DynamicTipRecommendationService tipRecommendationService;

    private User testCreator;

    @BeforeEach
    void setUp() {
        testCreator = new User();
        testCreator.setId(1L);
        // Default volatility 0
        when(creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldAdjustFactorBasedOnWhaleRisk_HighRisk() {
        // Arrange
        CreatorAdaptivePerformanceDTO performance = CreatorAdaptivePerformanceDTO.builder()
                .experimentsRun(10)
                .successRate(0.6)
                .averageRevenueLift(100.0)
                .averageRiskReduction(1.0)
                .momentum("POSITIVE")
                .engineConfidence(0.8)
                .build();
        when(performanceService.getCreatorMetrics(any())).thenReturn(performance);
        when(whaleRiskModel.calculateRisk(any())).thenReturn(0.85);

        // Act
        TipRecommendationResponse response = tipRecommendationService.generateForCreator(testCreator);

        // Assert
        // Initial factor = 1.0 + 0.15 (confidence > 0.75) + 0.10 (momentum positive) = 1.25
        // Whale Risk > 0.80 -> factor = 0.85
        // Median Tip = 5.00
        // Recommended Final = 5.00 * 0.85 = 4.25
        // Rounded to 0.50 -> 4.50 (Wait, 4.25 rounded to nearest 0.50 is 4.50 if using Math.round(v*2)/2)
        // Math.round(4.25 * 2) / 2 = Math.round(8.5) / 2 = 9 / 2 = 4.5
        assertEquals(BigDecimal.valueOf(4.50).setScale(2), response.getRecommendedMinimumTip());
    }

    @Test
    void shouldAdjustFactorBasedOnWhaleRisk_MediumRisk() {
        // Arrange
        CreatorAdaptivePerformanceDTO performance = CreatorAdaptivePerformanceDTO.builder()
                .experimentsRun(10)
                .successRate(0.6)
                .averageRevenueLift(100.0)
                .averageRiskReduction(1.0)
                .momentum("POSITIVE")
                .engineConfidence(0.8)
                .build();
        when(performanceService.getCreatorMetrics(any())).thenReturn(performance);
        when(whaleRiskModel.calculateRisk(any())).thenReturn(0.7);

        // Act
        TipRecommendationResponse response = tipRecommendationService.generateForCreator(testCreator);

        // Assert
        // Initial factor = 1.0 + 0.15 + 0.10 = 1.25
        // Whale Risk > 0.65 -> factor = min(1.25, 0.95) = 0.95
        // Recommended Final = 5.00 * 0.95 = 4.75
        // Math.round(4.75 * 2) / 2 = Math.round(9.5) / 2 = 10 / 2 = 5.0
        assertEquals(BigDecimal.valueOf(5.00).setScale(2), response.getRecommendedMinimumTip());
    }
}








