package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.CreatorAnalyticsResponseDTO;
import com.joinlivora.backend.analytics.dto.CreatorEarningsBreakdownDTO;
import com.joinlivora.backend.analytics.dto.TopContentDTO;
import com.joinlivora.backend.monetization.HighlightedChatMessageRepository;
import com.joinlivora.backend.monetization.PpvPurchaseRepository;
import com.joinlivora.backend.payout.*;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorAnalyticsServiceTest {

    @Mock
    private CreatorAnalyticsRepository creatorAnalyticsRepository;
    @Mock
    private CreatorEarningRepository earningRepository;
    @Mock
    private LegacyCreatorProfileRepository creatorProfileRepository;
    @Mock
    private PpvPurchaseRepository ppvPurchaseRepository;
    @Mock
    private HighlightedChatMessageRepository highlightedChatMessageRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;
    @Mock
    private ContentAnalyticsRepository contentAnalyticsRepository;
    @Mock
    private CreatorTopContentRepository creatorTopContentRepository;
    @Mock
    private CreatorStatsRepository creatorStatsRepository;

    private CreatorAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new CreatorAnalyticsService(
                creatorAnalyticsRepository,
                earningRepository,
                creatorProfileRepository,
                ppvPurchaseRepository,
                highlightedChatMessageRepository,
                analyticsEventRepository,
                contentAnalyticsRepository,
                creatorTopContentRepository,
                creatorStatsRepository
        );
    }

    @Test
    void getAnalytics_ShouldReturnMappedDtos() {
        // Given
        User creator = new User();
        creator.setId(1L);
        UUID profileId = UUID.randomUUID();
        LegacyCreatorProfile profile = LegacyCreatorProfile.builder().id(profileId).build();
        
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 7);
        
        CreatorAnalytics analytics = CreatorAnalytics.builder()
                .date(from)
                .totalEarnings(new BigDecimal("100.00"))
                .uniqueViewers(50)
                .subscriptionsCount(5)
                .returningViewers(10)
                .avgSessionDuration(300)
                .messagesPerViewer(2.5)
                .build();
        
        when(creatorProfileRepository.findByUser(creator)).thenReturn(Optional.of(profile));
        when(creatorAnalyticsRepository.findAllByCreatorIdAndDateBetweenOrderByDateAsc(profileId, from, to))
                .thenReturn(List.of(analytics));

        // When
        List<CreatorAnalyticsResponseDTO> result = analyticsService.getAnalytics(creator, from, to);

        // Then
        assertEquals(1, result.size());
        assertEquals(from, result.get(0).date());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.get(0).earnings()));
        assertEquals(50, result.get(0).viewers());
        assertEquals(5, result.get(0).subscriptions());
        assertEquals(10, result.get(0).returningViewers());
        assertEquals(300, result.get(0).avgSessionDuration());
        assertEquals(2.5, result.get(0).messagesPerViewer());
    }

    @Test
    void getEarningsBreakdown_ShouldAggregateValues() {
        // Given
        User creator = new User();
        creator.setId(1L);
        UUID profileId = UUID.randomUUID();
        LegacyCreatorProfile profile = LegacyCreatorProfile.builder().id(profileId).build();

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 2);

        CreatorAnalytics a1 = CreatorAnalytics.builder()
                .date(from)
                .subscriptionEarnings(new BigDecimal("50.00"))
                .ppvEarnings(new BigDecimal("20.00"))
                .tipsEarnings(new BigDecimal("10.00"))
                .liveStreamEarnings(new BigDecimal("5.00"))
                .build();

        CreatorAnalytics a2 = CreatorAnalytics.builder()
                .date(to)
                .subscriptionEarnings(new BigDecimal("30.00"))
                .ppvEarnings(new BigDecimal("10.00"))
                .tipsEarnings(new BigDecimal("5.00"))
                .liveStreamEarnings(new BigDecimal("2.00"))
                .build();

        when(creatorProfileRepository.findByUser(creator)).thenReturn(Optional.of(profile));
        when(creatorAnalyticsRepository.findAllByCreatorIdAndDateBetweenOrderByDateAsc(profileId, from, to))
                .thenReturn(List.of(a1, a2));

        // When
        CreatorEarningsBreakdownDTO result = analyticsService.getEarningsBreakdown(creator, from, to);

        // Then
        assertEquals(0, new BigDecimal("80.00").compareTo(result.subscriptions()));
        assertEquals(0, new BigDecimal("30.00").compareTo(result.ppv()));
        assertEquals(0, new BigDecimal("15.00").compareTo(result.tips()));
        assertEquals(0, new BigDecimal("7.00").compareTo(result.liveStream()));
    }

    @Test
    void getTopContent_ShouldReturnMappedDtos() {
        // Given
        User creator = new User();
        creator.setId(1L);
        UUID profileId = UUID.randomUUID();
        LegacyCreatorProfile profile = LegacyCreatorProfile.builder().id(profileId).build();

        UUID ppvId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        CreatorTopContent topPpv = CreatorTopContent.builder()
                .contentId(ppvId)
                .contentType("PPV")
                .title("PPV 1")
                .totalRevenue(new BigDecimal("100.00"))
                .rank(1)
                .build();

        CreatorTopContent topStream = CreatorTopContent.builder()
                .contentId(roomId)
                .contentType("STREAM")
                .title("Stream 1")
                .totalRevenue(new BigDecimal("50.00"))
                .rank(1)
                .build();

        when(creatorProfileRepository.findByUser(creator)).thenReturn(Optional.of(profile));
        when(creatorTopContentRepository.findAllByCreatorIdOrderByRankAsc(profileId))
                .thenReturn(List.of(topPpv, topStream));

        // When
        TopContentDTO result = analyticsService.getTopContent(creator);

        // Then
        assertEquals(1, result.topPpvContent().size());
        assertEquals("PPV 1", result.topPpvContent().get(0).title());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.topPpvContent().get(0).revenue()));

        assertEquals(1, result.topLiveStreams().size());
        assertEquals("Stream 1", result.topLiveStreams().get(0).title());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.topLiveStreams().get(0).revenue()));
    }

    @Test
    void generateDailyAnalytics_ShouldAggregateAndSave() {
        // Given
        LocalDate date = LocalDate.of(2026, 1, 1);
        User creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@example.com");

        UUID creatorProfileId = UUID.randomUUID();
        LegacyCreatorProfile profile = LegacyCreatorProfile.builder()
                .id(creatorProfileId)
                .user(creator)
                .build();

        when(earningRepository.findCreatorsWithEarningsInPeriod(any(), any())).thenReturn(List.of(creator));
        when(creatorProfileRepository.findByUser(creator)).thenReturn(Optional.of(profile));
        when(creatorAnalyticsRepository.findByCreatorIdAndDate(creatorProfileId, date)).thenReturn(Optional.empty());

        when(earningRepository.sumNetEarningsByCreatorAndPeriod(eq(creator), any(), any()))
                .thenReturn(new BigDecimal("120.00"));
        when(earningRepository.sumNetEarningsByCreatorAndSourceAndPeriod(eq(creator), eq(EarningSource.SUBSCRIPTION), any(), any()))
                .thenReturn(new BigDecimal("20.00"));
        when(earningRepository.sumNetEarningsByCreatorAndSourceAndPeriod(eq(creator), eq(EarningSource.PPV), any(), any()))
                .thenReturn(new BigDecimal("60.00"));
        when(earningRepository.sumNetEarningsByCreatorAndSourceAndPeriod(eq(creator), eq(EarningSource.TIP), any(), any()))
                .thenReturn(new BigDecimal("30.00"));
        when(earningRepository.sumNetEarningsByCreatorAndSourceAndPeriod(eq(creator), eq(EarningSource.HIGHLIGHTED_CHAT), any(), any()))
                .thenReturn(new BigDecimal("10.00"));
        when(earningRepository.countByCreatorAndSourceAndPeriod(eq(creator), eq(EarningSource.SUBSCRIPTION), any(), any()))
                .thenReturn(2L);

        when(analyticsEventRepository.countUniqueViewersByCreatorAndPeriod(eq("1"), any(), any()))
                .thenReturn(50L);
        when(analyticsEventRepository.countReturningViewersByCreatorAndPeriod(eq("1"), any(), any()))
                .thenReturn(10L);
        when(analyticsEventRepository.sumSessionDurationByCreatorAndPeriod(eq("1"), any(), any()))
                .thenReturn(5000L);
        when(analyticsEventRepository.countSessionsWithDurationByCreatorAndPeriod(eq("1"), any(), any()))
                .thenReturn(20L);
        when(analyticsEventRepository.countChatMessagesByCreatorAndPeriod(eq("1"), any(), any()))
                .thenReturn(100L);

        java.util.List<Object[]> ppvRev = new java.util.ArrayList<>();
        ppvRev.add(new Object[]{UUID.randomUUID(), "PPV", new BigDecimal("60.00")});
        when(ppvPurchaseRepository.findContentRevenueByCreatorAndPeriod(eq(creator), any(), any()))
                .thenReturn(ppvRev);

        java.util.List<Object[]> liveStreamRev = new java.util.ArrayList<>();
        liveStreamRev.add(new Object[]{UUID.randomUUID(), "Stream", new BigDecimal("10.00")});
        when(highlightedChatMessageRepository.findStreamRevenueByCreatorAndPeriod(eq(creator), any(), any()))
                .thenReturn(liveStreamRev);
        
        java.util.List<Object[]> topPpv = new java.util.ArrayList<>();
        topPpv.add(new Object[]{UUID.randomUUID(), "Top PPV", new BigDecimal("100.00")});
        when(ppvPurchaseRepository.findTopContentByCreator(eq(creator), any())).thenReturn(topPpv);

        java.util.List<Object[]> topStream = new java.util.ArrayList<>();
        topStream.add(new Object[]{UUID.randomUUID(), "Top Stream", new BigDecimal("50.00")});
        when(highlightedChatMessageRepository.findTopStreamsByCreator(eq(creator), any())).thenReturn(topStream);
        
        when(earningRepository.sumNetEarningsByCreatorAndSince(eq(creator), any())).thenReturn(new BigDecimal("1000.00"));
        when(creatorStatsRepository.findById(creatorProfileId)).thenReturn(Optional.empty());

        // When
        analyticsService.generateDailyAnalytics(date);

        // Then
        ArgumentCaptor<CreatorAnalytics> analyticsCaptor = ArgumentCaptor.forClass(CreatorAnalytics.class);
        verify(creatorAnalyticsRepository).save(analyticsCaptor.capture());

        verify(contentAnalyticsRepository, atLeastOnce()).save(any());
        verify(creatorTopContentRepository, atLeastOnce()).save(any());
        verify(creatorStatsRepository).save(any());

        CreatorAnalytics saved = analyticsCaptor.getValue();
        assertEquals(creatorProfileId, saved.getCreatorId());
        assertEquals(date, saved.getDate());
        assertEquals(0, new BigDecimal("120.00").compareTo(saved.getTotalEarnings()));
        assertEquals(0, new BigDecimal("20.00").compareTo(saved.getSubscriptionEarnings()));
        assertEquals(0, new BigDecimal("60.00").compareTo(saved.getPpvEarnings()));
        assertEquals(0, new BigDecimal("30.00").compareTo(saved.getTipsEarnings()));
        assertEquals(0, new BigDecimal("10.00").compareTo(saved.getLiveStreamEarnings()));
        assertEquals(2, saved.getSubscriptionsCount());
        assertEquals(50, saved.getUniqueViewers());
        assertEquals(10, saved.getReturningViewers());
        assertEquals(250, saved.getAvgSessionDuration()); // 5000 / 20 = 250
        assertEquals(2.0, saved.getMessagesPerViewer()); // 100 / 50 = 2.0
    }

    @Test
    void generateDailyAnalytics_AlreadyExists_ShouldSkip() {
        // Given
        LocalDate date = LocalDate.of(2026, 1, 1);
        User creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@example.com");

        UUID creatorProfileId = UUID.randomUUID();
        LegacyCreatorProfile profile = LegacyCreatorProfile.builder()
                .id(creatorProfileId)
                .user(creator)
                .build();

        when(earningRepository.findCreatorsWithEarningsInPeriod(any(), any())).thenReturn(List.of(creator));
        when(creatorProfileRepository.findByUser(creator)).thenReturn(Optional.of(profile));
        when(creatorAnalyticsRepository.findByCreatorIdAndDate(creatorProfileId, date))
                .thenReturn(Optional.of(new CreatorAnalytics()));

        // When
        analyticsService.generateDailyAnalytics(date);

        // Then
        verify(creatorAnalyticsRepository, never()).save(any());
    }
}









