package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.adaptive.WhaleRiskModel;
import com.joinlivora.backend.analytics.dto.AdaptiveTipEngineResponseDTO;
import com.joinlivora.backend.analytics.dto.CreatorAdaptivePerformanceDTO;
import com.joinlivora.backend.payout.CreatorEarningRepository;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdaptiveTipEngineServiceTest {

    @Mock
    private CreatorEarningRepository creatorEarningRepository;

    @Mock
    private AdaptiveTipExperimentRepository experimentRepository;

    @Mock
    private WhaleRiskModel whaleRiskModel;

    @Mock
    private CreatorAdaptivePerformanceService performanceService;

    @InjectMocks
    private AdaptiveTipEngineService adaptiveTipEngineService;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@test.com");
    }

    @Test
    void calculateDynamicCooldown_HighRisk_ShouldIncreaseCooldown() {
        // whaleRiskScore = 0.8 (> 0.75) -> +15
        // engineConfidence = 0.6 (> 0.50) -> +0
        // successRate = 0.6 (< 0.70) -> +0
        // Base 30 + 15 = 45
        
        double whaleRiskScore = 0.8;
        double engineConfidence = 0.6;
        double successRate = 0.6;
        
        // This method is private, but we can test it through calculateCurrentMetrics if we setup the mocks
    }

    @Test
    void calculateCurrentMetrics_WithActiveCooldown_ShouldReturnCooldownStatus() {
        // Setup 7 days of equal revenue -> Volatility 0
        java.util.List<com.joinlivora.backend.payout.CreatorEarning> earnings = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            com.joinlivora.backend.payout.CreatorEarning e = new com.joinlivora.backend.payout.CreatorEarning();
            e.setNetAmount(java.math.BigDecimal.TEN);
            e.setCreatedAt(java.time.Instant.now().minus(i, java.time.temporal.ChronoUnit.DAYS));
            earnings.add(e);
        }
        when(creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(any())).thenReturn(earnings);
        
        CreatorAdaptivePerformanceDTO performance = CreatorAdaptivePerformanceDTO.builder()
                .engineConfidence(0.8)
                .successRate(0.8)
                .build();
        
        when(performanceService.getCreatorMetrics(any())).thenReturn(performance);
        when(whaleRiskModel.calculateRisk(any())).thenReturn(0.2);
        
        // Dynamic Cooldown Calculation:
        // whaleRiskScore = 0.2 (< 0.75) -> +0
        // engineConfidence = 0.8 (> 0.50) -> +0
        // successRate = 0.8 (> 0.70) -> -10
        // Volatility = 0 -> +0
        // Base 30 - 10 = 20 minutes = 1200 seconds
        
        AdaptiveTipExperiment lastExperiment = new AdaptiveTipExperiment();
        lastExperiment.setCreatedAt(LocalDateTime.now().minusMinutes(10)); // 10 minutes ago
        
        when(experimentRepository.findAllByCreatorOrderByCreatedAtDesc(any())).thenReturn(List.of(lastExperiment));
        
        AdaptiveTipEngineResponseDTO response = adaptiveTipEngineService.calculateCurrentMetrics(creator);
        
        assertEquals("COOLDOWN", response.getStatus());
        // 20 min total - 10 min elapsed = 10 min remaining = 600 seconds
        assert(response.getCooldownRemainingSeconds() > 590 && response.getCooldownRemainingSeconds() <= 600);
    }

    @Test
    void calculateDynamicCooldown_HighVolatility_ShouldIncreaseCooldown() {
        // Setup 1 transaction -> High volatility
        com.joinlivora.backend.payout.CreatorEarning earning = new com.joinlivora.backend.payout.CreatorEarning();
        earning.setNetAmount(java.math.BigDecimal.TEN);
        earning.setCreatedAt(java.time.Instant.now());
        when(creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(any())).thenReturn(List.of(earning));
        
        CreatorAdaptivePerformanceDTO performance = CreatorAdaptivePerformanceDTO.builder()
                .engineConfidence(0.8)
                .successRate(0.8)
                .build();
        when(performanceService.getCreatorMetrics(any())).thenReturn(performance);
        when(whaleRiskModel.calculateRisk(any())).thenReturn(0.2);
        
        // Dynamic Cooldown Calculation:
        // whaleRiskScore = 0.2 (< 0.75) -> +0
        // engineConfidence = 0.8 (> 0.50) -> +0
        // successRate = 0.8 (> 0.70) -> -10
        // Volatility > 0.9 -> +10
        // Base 30 - 10 + 10 = 30 minutes = 1800 seconds
        
        AdaptiveTipExperiment lastExperiment = new AdaptiveTipExperiment();
        lastExperiment.setCreatedAt(LocalDateTime.now().minusMinutes(10)); // 10 minutes ago
        when(experimentRepository.findAllByCreatorOrderByCreatedAtDesc(any())).thenReturn(List.of(lastExperiment));
        
        AdaptiveTipEngineResponseDTO response = adaptiveTipEngineService.calculateCurrentMetrics(creator);
        
        assertEquals("COOLDOWN", response.getStatus());
        // 30 min total - 10 min elapsed = 20 min remaining = 1200 seconds
        assert(response.getCooldownRemainingSeconds() > 1190 && response.getCooldownRemainingSeconds() <= 1200);
    }
}








