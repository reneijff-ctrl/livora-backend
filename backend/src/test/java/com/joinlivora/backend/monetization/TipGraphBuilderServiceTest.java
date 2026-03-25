package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.TipGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TipGraphBuilderServiceTest {

    @Mock
    private TipRepository tipRepository;

    @InjectMocks
    private TipGraphBuilderService tipGraphBuilderService;

    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();
    }

    @Test
    void buildGraph_ShouldMapResultsCorrectly() {
        Instant since = now.minus(30, ChronoUnit.DAYS);
        
        Object[] row1 = new Object[]{
                1L, // tipper
                2L, // creator
                new BigDecimal("100.00"), // totalAmount
                5L, // tipCount
                now.minus(10, ChronoUnit.DAYS), // firstTipAt
                now.minus(1, ChronoUnit.DAYS) // lastTipAt
        };
        
        Object[] row2 = new Object[]{
                3L, // tipper
                2L, // creator
                new BigDecimal("50.00"), // totalAmount
                2L, // tipCount
                now.minus(5, ChronoUnit.DAYS), // firstTipAt
                now.minus(5, ChronoUnit.DAYS) // lastTipAt
        };

        when(tipRepository.aggregateTips(since)).thenReturn(List.of(row1, row2));

        List<TipGraph> graph = tipGraphBuilderService.buildGraph(since);

        assertThat(graph).hasSize(2);
        
        TipGraph node1 = graph.get(0);
        assertThat(node1.getTipperUserId()).isEqualTo(new UUID(0L, 1L));
        assertThat(node1.getCreatorUserId()).isEqualTo(new UUID(0L, 2L));
        assertThat(node1.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(node1.getTipCount()).isEqualTo(5L);
        assertThat(node1.getFirstTipAt()).isEqualTo(row1[4]);
        assertThat(node1.getLastTipAt()).isEqualTo(row1[5]);

        TipGraph node2 = graph.get(1);
        assertThat(node2.getTipperUserId()).isEqualTo(new UUID(0L, 3L));
        assertThat(node2.getCreatorUserId()).isEqualTo(new UUID(0L, 2L));
        assertThat(node2.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(node2.getTipCount()).isEqualTo(2L);
    }

    @Test
    void buildGraph_EmptyResults_ShouldReturnEmptyList() {
        when(tipRepository.aggregateTips(any())).thenReturn(List.of());
        
        List<TipGraph> graph = tipGraphBuilderService.buildGraph(now);
        
        assertThat(graph).isEmpty();
    }
}








