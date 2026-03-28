package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.CreatorAnalyticsResponseDTO;
import com.joinlivora.backend.analytics.dto.CreatorEarningsBreakdownDTO;
import com.joinlivora.backend.analytics.dto.TopContentDTO;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.monetization.HighlightedChatMessageRepository;
import com.joinlivora.backend.monetization.PpvPurchaseRepository;
import com.joinlivora.backend.payout.CreatorEarningRepository;
import com.joinlivora.backend.payout.LegacyCreatorProfile;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
import com.joinlivora.backend.payout.EarningSource;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorAnalyticsService {

    private final CreatorAnalyticsRepository creatorAnalyticsRepository;
    private final CreatorEarningRepository earningRepository;
    private final LegacyCreatorProfileRepository creatorProfileRepository;
    private final PpvPurchaseRepository ppvPurchaseRepository;
    private final HighlightedChatMessageRepository highlightedChatMessageRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final ContentAnalyticsRepository contentAnalyticsRepository;
    private final CreatorTopContentRepository creatorTopContentRepository;
    private final CreatorStatsRepository creatorStatsRepository;

    @Transactional(readOnly = true)
    public List<CreatorAnalyticsResponseDTO> getAnalytics(User creator, LocalDate from, LocalDate to) {
        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(creator)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found"));

        List<CreatorAnalyticsResponseDTO> preAggregated = getAnalyticsByProfileId(profile.getId(), from, to);
        if (!preAggregated.isEmpty()) {
            return preAggregated;
        }

        // Fallback: compute daily analytics from real earnings data when
        // the pre-aggregated creator_analytics table has no entries (batch job hasn't run yet)
        return computeDailyAnalyticsFromEarnings(creator, from, to);
    }

    private List<CreatorAnalyticsResponseDTO> computeDailyAnalyticsFromEarnings(User creator, LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Object[]> dailyRows = earningRepository.findDailyEarningsSummaryByCreatorAndPeriod(creator.getId(), start, end);
        log.info("Daily analytics count: {}", dailyRows.size());

        return dailyRows.stream()
                .map(row -> {
                    LocalDate date = row[0] instanceof LocalDate ld ? ld
                            : ((java.sql.Date) row[0]).toLocalDate();
                    BigDecimal earnings = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                    long uniqueUsers = row[2] != null ? ((Number) row[2]).longValue() : 0;
                    return new CreatorAnalyticsResponseDTO(
                            date,
                            earnings,
                            uniqueUsers,  // viewers (proxy: unique paying users)
                            0,            // subscriptions (not available from earnings alone)
                            0,            // returningViewers
                            0,            // avgSessionDuration
                            0.0           // messagesPerViewer
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CreatorAnalyticsResponseDTO> getAnalyticsByProfileId(UUID profileId, LocalDate from, LocalDate to) {
        List<CreatorAnalytics> analytics = creatorAnalyticsRepository.findAllByCreatorIdAndDateBetweenOrderByDateAsc(
                profileId, from, to);

        return analytics.stream()
                .map(a -> new CreatorAnalyticsResponseDTO(
                        a.getDate(),
                        a.getTotalEarnings(),
                        a.getUniqueViewers(),
                        a.getSubscriptionsCount(),
                        a.getReturningViewers(),
                        a.getAvgSessionDuration(),
                        a.getMessagesPerViewer()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public CreatorEarningsBreakdownDTO getEarningsBreakdown(User creator, LocalDate from, LocalDate to) {
        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(creator)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found"));

        List<CreatorAnalytics> analytics = creatorAnalyticsRepository.findAllByCreatorIdAndDateBetweenOrderByDateAsc(
                profile.getId(), from, to);

        BigDecimal subscriptions = BigDecimal.ZERO;
        BigDecimal ppv = BigDecimal.ZERO;
        BigDecimal tips = BigDecimal.ZERO;
        BigDecimal liveStream = BigDecimal.ZERO;

        for (CreatorAnalytics a : analytics) {
            subscriptions = subscriptions.add(a.getSubscriptionEarnings());
            ppv = ppv.add(a.getPpvEarnings());
            tips = tips.add(a.getTipsEarnings());
            liveStream = liveStream.add(a.getLiveStreamEarnings());
        }

        return new CreatorEarningsBreakdownDTO(subscriptions, ppv, tips, liveStream);
    }

    @Transactional(readOnly = true)
    public TopContentDTO getTopContent(User creator) {
        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(creator)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found"));

        List<CreatorTopContent> topContent = creatorTopContentRepository.findAllByCreatorIdOrderByRankAsc(profile.getId());

        List<TopContentDTO.ContentRevenueDTO> topPpv = topContent.stream()
                .filter(c -> "PPV".equals(c.getContentType()))
                .map(c -> new TopContentDTO.ContentRevenueDTO(c.getContentId(), c.getTitle(), c.getTotalRevenue()))
                .toList();

        List<TopContentDTO.ContentRevenueDTO> topStreams = topContent.stream()
                .filter(c -> "STREAM".equals(c.getContentType()))
                .map(c -> new TopContentDTO.ContentRevenueDTO(c.getContentId(), c.getTitle(), c.getTotalRevenue()))
                .toList();

        return new TopContentDTO(topPpv, topStreams);
    }

    @Transactional
    public void generateDailyAnalytics(LocalDate date) {
        log.info("Generating daily analytics for {}", date);

        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<User> creators = earningRepository.findCreatorsWithEarningsInPeriod(start, end);
        log.info("Found {} creators with activity on {}", creators.size(), date);

        for (User creator : creators) {
            try {
                LegacyCreatorProfile profile = creatorProfileRepository.findByUser(creator).orElse(null);
                if (profile == null) {
                    log.warn("No LegacyCreatorProfile found for creator {}, skipping analytics", creator.getEmail());
                    continue;
                }

                UUID creatorId = profile.getId();
                if (creatorAnalyticsRepository.findByCreatorIdAndDate(creatorId, date).isPresent()) {
                    log.info("Analytics for creator {} and date {} already exists, skipping", creatorId, date);
                    continue;
                }

                CreatorAnalytics analytics = aggregateForCreator(creator, creatorId, date, start, end);
                creatorAnalyticsRepository.save(analytics);
                
                // Content Analytics
                saveContentAnalytics(creator, creatorId, date, start, end);
                
                // Update Top Content
                updateCreatorTopContent(creator, creatorId);
                
                // Update Lifetime Stats
                updateCreatorStats(creator, creatorId);

                log.debug("Saved analytics for creator {} for {}", creatorId, date);
            } catch (Exception e) {
                log.error("Failed to generate analytics for creator {} on {}", creator.getEmail(), date, e);
            }
        }
        log.info("Finished daily analytics generation for {}", date);
    }

    private void saveContentAnalytics(User creator, UUID creatorId, LocalDate date, Instant start, Instant end) {
        List<Object[]> ppvRevenue = ppvPurchaseRepository.findContentRevenueByCreatorAndPeriod(creator, start, end);
        for (Object[] row : ppvRevenue) {
            contentAnalyticsRepository.save(ContentAnalytics.builder()
                    .contentId((UUID) row[0])
                    .contentType("PPV")
                    .creatorId(creatorId)
                    .date(date)
                    .revenue((BigDecimal) row[2])
                    .build());
        }

        List<Object[]> streamRevenue = highlightedChatMessageRepository.findStreamRevenueByCreatorAndPeriod(creator, start, end);
        for (Object[] row : streamRevenue) {
            contentAnalyticsRepository.save(ContentAnalytics.builder()
                    .contentId((UUID) row[0])
                    .contentType("STREAM")
                    .creatorId(creatorId)
                    .date(date)
                    .revenue((BigDecimal) row[2])
                    .build());
        }
    }

    private void updateCreatorTopContent(User creator, UUID creatorId) {
        creatorTopContentRepository.deleteAllByCreatorId(creatorId);

        List<Object[]> topPpv = ppvPurchaseRepository.findTopContentByCreator(creator, PageRequest.of(0, 5));
        int rank = 1;
        for (Object[] row : topPpv) {
            creatorTopContentRepository.save(CreatorTopContent.builder()
                    .creatorId(creatorId)
                    .contentType("PPV")
                    .contentId((UUID) row[0])
                    .title((String) row[1])
                    .totalRevenue((BigDecimal) row[2])
                    .rank(rank++)
                    .updatedAt(Instant.now())
                    .build());
        }

        List<Object[]> topStreams = highlightedChatMessageRepository.findTopStreamsByCreator(creator, PageRequest.of(0, 5));
        rank = 1;
        for (Object[] row : topStreams) {
            creatorTopContentRepository.save(CreatorTopContent.builder()
                    .creatorId(creatorId)
                    .contentType("STREAM")
                    .contentId((UUID) row[0])
                    .title((String) row[1])
                    .totalRevenue((BigDecimal) row[2])
                    .rank(rank++)
                    .updatedAt(Instant.now())
                    .build());
        }
    }

    private void updateCreatorStats(User creator, UUID creatorId) {
        BigDecimal totalNet = earningRepository.sumNetEarningsByCreatorAndSince(creator, Instant.EPOCH);
        long subCount = earningRepository.countByCreatorAndSource(creator, EarningSource.SUBSCRIPTION);
        long tipCount = earningRepository.countByCreatorAndSource(creator, EarningSource.TIP);
        long highlightCount = earningRepository.countByCreatorAndSource(creator, EarningSource.HIGHLIGHTED_CHAT);

        // Ensure stats record exists
        if (!creatorStatsRepository.existsById(creatorId)) {
            try {
                creatorStatsRepository.save(CreatorStats.builder().creatorId(creatorId).updatedAt(Instant.now()).build());
                creatorStatsRepository.flush();
            } catch (Exception e) {
                // Ignore if created concurrently
            }
        }

        // Use atomic update to avoid overwriting real-time increments to other fields
        creatorStatsRepository.updateCreatorTotals(
                creatorId,
                totalNet != null ? totalNet : BigDecimal.ZERO,
                subCount,
                tipCount,
                highlightCount,
                Instant.now()
        );
    }

    private CreatorAnalytics aggregateForCreator(User creator, UUID creatorId, LocalDate date, Instant start, Instant end) {
        BigDecimal totalEarnings = earningRepository.sumNetEarningsByCreatorAndPeriod(creator, start, end);
        BigDecimal subEarnings = earningRepository.sumNetEarningsByCreatorAndSourceAndPeriod(creator, EarningSource.SUBSCRIPTION, start, end);
        BigDecimal ppvEarnings = earningRepository.sumNetEarningsByCreatorAndSourceAndPeriod(creator, EarningSource.PPV, start, end);
        BigDecimal tipsEarnings = earningRepository.sumNetEarningsByCreatorAndSourceAndPeriod(creator, EarningSource.TIP, start, end);
        BigDecimal liveStreamEarnings = earningRepository.sumNetEarningsByCreatorAndSourceAndPeriod(creator, EarningSource.HIGHLIGHTED_CHAT, start, end);

        long subCount = earningRepository.countByCreatorAndSourceAndPeriod(creator, EarningSource.SUBSCRIPTION, start, end);

        // New Metrics
        String creatorIdStr = creator.getId().toString();
        long uniqueViewers = analyticsEventRepository.countUniqueViewersByCreatorAndPeriod(creatorIdStr, start, end);
        long returningViewers = analyticsEventRepository.countReturningViewersByCreatorAndPeriod(creatorIdStr, start, end);
        long totalDuration = analyticsEventRepository.sumSessionDurationByCreatorAndPeriod(creatorIdStr, start, end);
        long sessionCountWithDuration = analyticsEventRepository.countSessionsWithDurationByCreatorAndPeriod(creatorIdStr, start, end);
        long chatMessages = analyticsEventRepository.countChatMessagesByCreatorAndPeriod(creatorIdStr, start, end);

        long avgDuration = sessionCountWithDuration > 0 ? totalDuration / sessionCountWithDuration : 0;
        double messagesPerViewer = uniqueViewers > 0 ? (double) chatMessages / uniqueViewers : 0.0;

        return CreatorAnalytics.builder()
                .creatorId(creatorId)
                .date(date)
                .totalEarnings(totalEarnings != null ? totalEarnings : BigDecimal.ZERO)
                .subscriptionEarnings(subEarnings != null ? subEarnings : BigDecimal.ZERO)
                .ppvEarnings(ppvEarnings != null ? ppvEarnings : BigDecimal.ZERO)
                .tipsEarnings(tipsEarnings != null ? tipsEarnings : BigDecimal.ZERO)
                .liveStreamEarnings(liveStreamEarnings != null ? liveStreamEarnings : BigDecimal.ZERO)
                .subscriptionsCount(subCount)
                .totalViews(uniqueViewers) // Using uniqueViewers as totalViews for now
                .uniqueViewers(uniqueViewers)
                .returningViewers(returningViewers)
                .avgSessionDuration(avgDuration)
                .messagesPerViewer(messagesPerViewer)
                .build();
    }
}
