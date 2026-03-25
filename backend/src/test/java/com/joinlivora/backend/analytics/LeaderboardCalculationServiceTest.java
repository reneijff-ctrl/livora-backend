package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.LeaderboardResponseDto;
import com.joinlivora.backend.payout.LegacyCreatorProfile;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaderboardCalculationServiceTest {

    @Mock
    private CreatorAnalyticsRepository analyticsRepository;
    @Mock
    private LeaderboardEntryRepository leaderboardRepository;
    @Mock
    private LegacyCreatorProfileRepository creatorProfileRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private LeaderboardCalculationService calculationService;

    @BeforeEach
    void setUp() {
        calculationService = new LeaderboardCalculationService(analyticsRepository, leaderboardRepository, creatorProfileRepository, redisTemplate);
    }

    @Test
    void getLeaderboard_ShouldUseCache() {
        // Given
        LeaderboardPeriod period = LeaderboardPeriod.DAILY;
        String cacheKey = "leaderboard:DAILY";
        LeaderboardResponseDto dto = new LeaderboardResponseDto(1, UUID.randomUUID(), new BigDecimal("100.00"), 50, null);
        List<LeaderboardResponseDto> cachedData = List.of(dto);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cachedData);

        // When
        List<LeaderboardResponseDto> result = calculationService.getLeaderboard(period, null, 10);

        // Then
        assertEquals(1, result.size());
        assertEquals(dto.creatorId(), result.get(0).creatorId());
        verify(leaderboardRepository, never()).findMaxReferenceDateByPeriod(any());
    }

    @Test
    void getLeaderboard_OnCacheMiss_ShouldFetchAndCache() {
        // Given
        LeaderboardPeriod period = LeaderboardPeriod.DAILY;
        LocalDate date = LocalDate.of(2026, 1, 20);
        String cacheKey = "leaderboard:DAILY";
        UUID creatorId = UUID.randomUUID();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(leaderboardRepository.findMaxReferenceDateByPeriod(period)).thenReturn(Optional.of(date));

        LeaderboardEntry entry = LeaderboardEntry.builder()
                .rank(1)
                .creatorId(creatorId)
                .totalEarnings(new BigDecimal("100.00"))
                .totalViewers(50)
                .build();

        when(leaderboardRepository.findGlobalByPeriodAndReferenceDateOrderByRankAsc(eq(period), eq(date), any()))
                .thenReturn(List.of(entry));

        // When
        List<LeaderboardResponseDto> result = calculationService.getLeaderboard(period, null, 10);

        // Then
        assertEquals(1, result.size());
        assertEquals(creatorId, result.get(0).creatorId());
        verify(valueOperations).set(eq(cacheKey), any(), any());
    }

    @Test
    void calculateRankings_ShouldClearCache() {
        // Given
        LocalDate date = LocalDate.of(2026, 1, 20);
        when(analyticsRepository.findAllByDateBetween(any(), any())).thenReturn(List.of());
        when(creatorProfileRepository.findAllById(anySet())).thenReturn(List.of());
        
        String pattern = "leaderboard:DAILY*";
        Set<String> keys = Set.of("leaderboard:DAILY", "leaderboard:DAILY:GAMING");
        when(redisTemplate.keys(pattern)).thenReturn(keys);

        // When
        calculationService.calculateRankings(LeaderboardPeriod.DAILY, date);

        // Then
        verify(redisTemplate).delete(keys);
    }

    @Test
    void calculateRankings_Daily_ShouldAggregateAndSort() {
        // Given
        LocalDate date = LocalDate.of(2026, 1, 20);
        UUID creator1Id = UUID.randomUUID();
        UUID creator2Id = UUID.randomUUID();

        CreatorAnalytics a1 = CreatorAnalytics.builder()
                .creatorId(creator1Id)
                .date(date)
                .totalEarnings(new BigDecimal("100.00"))
                .uniqueViewers(50)
                .subscriptionsCount(5)
                .build();

        CreatorAnalytics a2 = CreatorAnalytics.builder()
                .creatorId(creator2Id)
                .date(date)
                .totalEarnings(new BigDecimal("200.00"))
                .uniqueViewers(30)
                .subscriptionsCount(2)
                .build();

        when(analyticsRepository.findAllByDateBetween(date, date)).thenReturn(List.of(a1, a2));
        when(creatorProfileRepository.findAllById(anySet())).thenReturn(List.of());

        // When
        calculationService.calculateRankings(LeaderboardPeriod.DAILY, date);

        // Then
        verify(leaderboardRepository).deleteByPeriodAndReferenceDate(LeaderboardPeriod.DAILY, date);
        ArgumentCaptor<List<LeaderboardEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(leaderboardRepository).saveAll(captor.capture());

        List<LeaderboardEntry> savedEntries = captor.getValue();
        assertEquals(2, savedEntries.size());

        // Creator 2 should be rank 1 (more earnings)
        LeaderboardEntry rank1 = savedEntries.stream().filter(e -> e.getRank() == 1).findFirst().orElseThrow();
        assertEquals(creator2Id, rank1.getCreatorId());
        assertEquals(0, new BigDecimal("200.00").compareTo(rank1.getTotalEarnings()));
        assertEquals(date, rank1.getReferenceDate());
        assertNull(rank1.getCategory());

        // Creator 1 should be rank 2
        LeaderboardEntry rank2 = savedEntries.stream().filter(e -> e.getRank() == 2).findFirst().orElseThrow();
        assertEquals(creator1Id, rank2.getCreatorId());
        assertEquals(0, new BigDecimal("100.00").compareTo(rank2.getTotalEarnings()));
        assertNull(rank2.getCategory());
    }

    @Test
    void calculateRankings_WithCategories_ShouldGenerateBothLeaderboards() {
        // Given
        LocalDate date = LocalDate.of(2026, 1, 20);
        UUID creator1Id = UUID.randomUUID();
        UUID creator2Id = UUID.randomUUID();

        CreatorAnalytics a1 = CreatorAnalytics.builder()
                .creatorId(creator1Id)
                .date(date)
                .totalEarnings(new BigDecimal("100.00"))
                .uniqueViewers(50)
                .build();

        CreatorAnalytics a2 = CreatorAnalytics.builder()
                .creatorId(creator2Id)
                .date(date)
                .totalEarnings(new BigDecimal("200.00"))
                .uniqueViewers(30)
                .build();

        when(analyticsRepository.findAllByDateBetween(date, date)).thenReturn(List.of(a1, a2));
        
        LegacyCreatorProfile p1 = LegacyCreatorProfile.builder().id(creator1Id).category("GAMING").build();
        LegacyCreatorProfile p2 = LegacyCreatorProfile.builder().id(creator2Id).category("GAMING").build();
        when(creatorProfileRepository.findAllById(anySet())).thenReturn(List.of(p1, p2));

        // When
        calculationService.calculateRankings(LeaderboardPeriod.DAILY, date);

        // Then
        ArgumentCaptor<List<LeaderboardEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(leaderboardRepository).saveAll(captor.capture());

        List<LeaderboardEntry> savedEntries = captor.getValue();
        // 2 global + 2 gaming = 4
        assertEquals(4, savedEntries.size());

        // Global check
        long globalCount = savedEntries.stream().filter(e -> e.getCategory() == null).count();
        assertEquals(2, globalCount);
        
        // Category check
        long gamingCount = savedEntries.stream().filter(e -> "GAMING".equals(e.getCategory())).count();
        assertEquals(2, gamingCount);

        // Rank check in gaming
        LeaderboardEntry gamingRank1 = savedEntries.stream()
                .filter(e -> "GAMING".equals(e.getCategory()) && e.getRank() == 1)
                .findFirst().orElseThrow();
        assertEquals(creator2Id, gamingRank1.getCreatorId());
    }

    @Test
    void calculateRankings_TieBreaker_ShouldUseViewers() {
        // Given
        LocalDate date = LocalDate.of(2026, 1, 20);
        UUID creator1Id = UUID.randomUUID();
        UUID creator2Id = UUID.randomUUID();

        CreatorAnalytics a1 = CreatorAnalytics.builder()
                .creatorId(creator1Id)
                .date(date)
                .totalEarnings(new BigDecimal("100.00"))
                .uniqueViewers(50)
                .build();

        CreatorAnalytics a2 = CreatorAnalytics.builder()
                .creatorId(creator2Id)
                .date(date)
                .totalEarnings(new BigDecimal("100.00"))
                .uniqueViewers(100)
                .build();

        when(analyticsRepository.findAllByDateBetween(date, date)).thenReturn(List.of(a1, a2));
        when(creatorProfileRepository.findAllById(anySet())).thenReturn(List.of());

        // When
        calculationService.calculateRankings(LeaderboardPeriod.DAILY, date);

        // Then
        ArgumentCaptor<List<LeaderboardEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(leaderboardRepository).saveAll(captor.capture());

        List<LeaderboardEntry> savedEntries = captor.getValue();
        
        // Creator 2 should be rank 1 (same earnings, more viewers)
        LeaderboardEntry rank1 = savedEntries.stream().filter(e -> e.getRank() == 1).findFirst().orElseThrow();
        assertEquals(creator2Id, rank1.getCreatorId());
        
        LeaderboardEntry rank2 = savedEntries.stream().filter(e -> e.getRank() == 2).findFirst().orElseThrow();
        assertEquals(creator1Id, rank2.getCreatorId());
    }

    @Test
    void calculateRankings_Weekly_ShouldAggregateCorrectRange() {
        // Given
        LocalDate date = LocalDate.of(2026, 1, 20);
        LocalDate startDate = date.minusDays(6); // 2026-01-14
        UUID creatorId = UUID.randomUUID();

        CreatorAnalytics a1 = CreatorAnalytics.builder()
                .creatorId(creatorId)
                .date(startDate)
                .totalEarnings(new BigDecimal("100.00"))
                .uniqueViewers(50)
                .build();

        CreatorAnalytics a2 = CreatorAnalytics.builder()
                .creatorId(creatorId)
                .date(date)
                .totalEarnings(new BigDecimal("50.00"))
                .uniqueViewers(20)
                .build();

        when(analyticsRepository.findAllByDateBetween(startDate, date)).thenReturn(List.of(a1, a2));
        when(creatorProfileRepository.findAllById(anySet())).thenReturn(List.of());

        // When
        calculationService.calculateRankings(LeaderboardPeriod.WEEKLY, date);

        // Then
        ArgumentCaptor<List<LeaderboardEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(leaderboardRepository).saveAll(captor.capture());

        List<LeaderboardEntry> savedEntries = captor.getValue();
        assertEquals(1, savedEntries.size());
        LeaderboardEntry entry = savedEntries.get(0);
        assertEquals(0, new BigDecimal("150.00").compareTo(entry.getTotalEarnings()));
        assertEquals(70, entry.getTotalViewers());
        assertEquals(LeaderboardPeriod.WEEKLY, entry.getPeriod());
        assertEquals(date, entry.getReferenceDate());
    }
}









