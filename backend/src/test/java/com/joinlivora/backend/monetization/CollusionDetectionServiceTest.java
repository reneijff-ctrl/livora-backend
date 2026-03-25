package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventRepository;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.monetization.dto.CollusionResult;
import com.joinlivora.backend.monetization.dto.TipGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollusionDetectionServiceTest {

    @Mock
    private TipGraphBuilderService tipGraphBuilderService;

    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    @InjectMocks
    private CollusionDetectionService collusionDetectionService;

    private UUID userId;
    private List<TipGraph> graph;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        graph = new ArrayList<>();
        when(tipGraphBuilderService.buildGraph(any())).thenReturn(graph);
    }

    @Test
    void detectCollusion_NoPatterns_ShouldReturnZero() {
        CollusionResult result = collusionDetectionService.detectCollusion(userId);
        assertEquals(0, result.getCollusionScore());
        assertTrue(result.getPatternTypes().isEmpty());
    }

    @Test
    void detectCollusion_RepeatedTipping_ShouldDetect() {
        UUID tipperId = UUID.randomUUID();
        graph.add(TipGraph.builder()
                .tipperUserId(tipperId)
                .creatorUserId(userId)
                .tipCount(11) // Trigger threshold
                .build());

        CollusionResult result = collusionDetectionService.detectCollusion(userId);
        
        assertTrue(result.getCollusionScore() >= 30);
        assertTrue(result.getPatternTypes().contains("REPEATED_TIPPING"));
    }

    @Test
    void detectCollusion_CircularTipping_ShouldDetect() {
        UUID otherId = UUID.randomUUID();
        // I tip other
        graph.add(TipGraph.builder()
                .tipperUserId(userId)
                .creatorUserId(otherId)
                .tipCount(1)
                .build());
        // Other tips me
        graph.add(TipGraph.builder()
                .tipperUserId(otherId)
                .creatorUserId(userId)
                .tipCount(1)
                .build());

        CollusionResult result = collusionDetectionService.detectCollusion(userId);
        
        assertTrue(result.getCollusionScore() >= 50);
        assertTrue(result.getPatternTypes().contains("CIRCULAR_TIPPING"));
    }

    @Test
    void detectCollusion_HighTipLowActivity_ShouldDetect() {
        // creator received tips from 6 different people
        for (int i = 0; i < 6; i++) {
            graph.add(TipGraph.builder()
                    .tipperUserId(UUID.randomUUID())
                    .creatorUserId(userId)
                    .tipCount(1)
                    .build());
        }

        // But creator has very few chat messages
        when(analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(anyLong(), eq(AnalyticsEventType.CHAT_MESSAGE_SENT), any()))
                .thenReturn(2L); // threshold is 5

        CollusionResult result = collusionDetectionService.detectCollusion(userId);
        
        assertTrue(result.getCollusionScore() >= 40);
        assertTrue(result.getPatternTypes().contains("HIGH_TIP_LOW_ACTIVITY"));
    }

    @Test
    void detectCollusion_ClusterFunding_ShouldDetect() {
        // creatorUserId (creator) received tips from 5 people
        for (int i = 0; i < 5; i++) {
            UUID tipperId = UUID.randomUUID();
            graph.add(TipGraph.builder()
                    .tipperUserId(tipperId)
                    .creatorUserId(userId)
                    .tipCount(1)
                    .build());
            
            // All these tippers have 0 chat activity
            when(analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(eq(tipperId.getLeastSignificantBits()), eq(AnalyticsEventType.CHAT_MESSAGE_SENT), any()))
                    .thenReturn(0L);
        }

        CollusionResult result = collusionDetectionService.detectCollusion(userId);
        
        assertTrue(result.getCollusionScore() >= 30);
        assertTrue(result.getPatternTypes().contains("CLUSTER_FUNDING"));
    }

    @Test
    void detectCollusion_CombinedPatterns_ShouldCapAt100() {
        UUID otherId = UUID.randomUUID();
        // Circular + Repeated
        graph.add(TipGraph.builder()
                .tipperUserId(userId)
                .creatorUserId(otherId)
                .tipCount(1)
                .build());
        graph.add(TipGraph.builder()
                .tipperUserId(otherId)
                .creatorUserId(userId)
                .tipCount(15)
                .build());

        CollusionResult result = collusionDetectionService.detectCollusion(userId);
        
        // 50 (Circular) + 30 (Repeated) = 80
        assertEquals(80, result.getCollusionScore());
        assertTrue(result.getPatternTypes().contains("CIRCULAR_TIPPING"));
        assertTrue(result.getPatternTypes().contains("REPEATED_TIPPING"));
    }
}








