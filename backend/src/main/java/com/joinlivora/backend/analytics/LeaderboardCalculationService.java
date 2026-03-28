package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.LeaderboardResponseDto;
import com.joinlivora.backend.payout.LegacyCreatorProfile;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LeaderboardCalculationService {

    private final CreatorAnalyticsRepository analyticsRepository;
    private final LeaderboardEntryRepository leaderboardRepository;
    private final LegacyCreatorProfileRepository creatorProfileRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public LeaderboardCalculationService(
            CreatorAnalyticsRepository analyticsRepository,
            LeaderboardEntryRepository leaderboardRepository,
            LegacyCreatorProfileRepository creatorProfileRepository,
            @Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.analyticsRepository = analyticsRepository;
        this.leaderboardRepository = leaderboardRepository;
        this.creatorProfileRepository = creatorProfileRepository;
        this.redisTemplate = redisTemplate;
        if (redisTemplate == null) {
            log.info("RedisTemplate not found, LeaderboardCalculationService cache will be disabled.");
        }
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<LeaderboardResponseDto> getLeaderboard(LeaderboardPeriod period, String category, int limit) {
        String cacheKey = getCacheKey(period, category);
        if (redisTemplate != null) {
            try {
                List<LeaderboardResponseDto> cached = (List<LeaderboardResponseDto>) redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    log.debug("Cache hit for leaderboard: {}", cacheKey);
                    return cached.stream().limit(limit).toList();
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve leaderboard from cache: {}", e.getMessage());
            }
        }

        // Find the most recent referenceId date for the given period
        LocalDate latestDate = leaderboardRepository.findMaxReferenceDateByPeriod(period).orElse(null);
        
        if (latestDate == null) {
            return List.of();
        }

        List<LeaderboardEntry> entries;
        // We fetch at least 100 entries to have a meaningful cache even if requested limit is small
        int fetchLimit = Math.max(limit, 100);

        if (category == null || category.isBlank()) {
            entries = leaderboardRepository.findGlobalByPeriodAndReferenceDateOrderByRankAsc(period, latestDate, PageRequest.of(0, fetchLimit));
        } else {
            entries = leaderboardRepository.findAllByPeriodAndReferenceDateAndCategoryOrderByRankAsc(period, latestDate, category, PageRequest.of(0, fetchLimit));
        }

        List<LeaderboardResponseDto> result = entries.stream()
                .map(e -> new LeaderboardResponseDto(e.getRank(), e.getCreatorId(), e.getTotalEarnings(), e.getTotalViewers(), e.getCategory()))
                .toList();

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, result, getTtl(period));
            } catch (Exception e) {
                log.warn("Failed to cache leaderboard: {}", e.getMessage());
            }
        }

        return result.stream().limit(limit).toList();
    }

    @Transactional
    public void calculateRankings(LeaderboardPeriod period, LocalDate date) {
        log.info("Calculating rankings for period {} and date {}", period, date);

        LocalDate startDate = getStartDate(period, date);
        LocalDate endDate = date;

        List<CreatorAnalytics> analyticsList = analyticsRepository.findAllByDateBetween(startDate, endDate);

        Map<UUID, List<CreatorAnalytics>> groupedByCreator = analyticsList.stream()
                .collect(Collectors.groupingBy(CreatorAnalytics::getCreatorId));

        Map<UUID, String> creatorCategories = creatorProfileRepository.findAllById(groupedByCreator.keySet())
                .stream()
                .filter(cp -> cp.getCategory() != null)
                .collect(Collectors.toMap(LegacyCreatorProfile::getId, LegacyCreatorProfile::getCategory));

        List<LeaderboardEntry> globalEntries = new ArrayList<>();
        Map<String, List<LeaderboardEntry>> categoryEntries = new HashMap<>();

        for (Map.Entry<UUID, List<CreatorAnalytics>> entry : groupedByCreator.entrySet()) {
            UUID creatorId = entry.getKey();
            List<CreatorAnalytics> analytics = entry.getValue();
            String category = creatorCategories.get(creatorId);

            // Global entry
            globalEntries.add(aggregate(creatorId, analytics, period, null));

            // Category entry
            if (category != null && !category.isBlank()) {
                categoryEntries.computeIfAbsent(category, k -> new ArrayList<>())
                        .add(aggregate(creatorId, analytics, period, category));
            }
        }

        List<LeaderboardEntry> allToSave = new ArrayList<>();
        rankAndCollect(globalEntries, date, allToSave);
        for (List<LeaderboardEntry> catEntries : categoryEntries.values()) {
            rankAndCollect(catEntries, date, allToSave);
        }

        leaderboardRepository.deleteByPeriodAndReferenceDate(period, date);
        leaderboardRepository.saveAll(allToSave);
        
        clearCache(period);
        
        log.info("Finished calculating rankings for period {} and date {}. Total entries: {}", period, date, allToSave.size());
    }

    private void clearCache(LeaderboardPeriod period) {
        if (redisTemplate == null) return;
        try {
            String pattern = "leaderboard:" + period + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Cleared {} cache keys for period {}", keys.size(), period);
            }
        } catch (Exception e) {
            log.warn("Failed to clear leaderboard cache for period {}: {}", period, e.getMessage());
        }
    }

    private String getCacheKey(LeaderboardPeriod period, String category) {
        if (category == null || category.isBlank()) {
            return "leaderboard:" + period;
        }
        return "leaderboard:" + period + ":" + category;
    }

    private Duration getTtl(LeaderboardPeriod period) {
        return switch (period) {
            case DAILY -> Duration.ofMinutes(10);
            case WEEKLY, MONTHLY -> Duration.ofMinutes(60);
        };
    }

    private void rankAndCollect(List<LeaderboardEntry> entries, LocalDate referenceDate, List<LeaderboardEntry> allToSave) {
        entries.sort(Comparator.comparing(LeaderboardEntry::getTotalEarnings).reversed()
                .thenComparing(Comparator.comparing(LeaderboardEntry::getTotalViewers).reversed()));
        
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            entry.setRank(i + 1);
            entry.setReferenceDate(referenceDate);
            allToSave.add(entry);
        }
    }

    private LocalDate getStartDate(LeaderboardPeriod period, LocalDate date) {
        return switch (period) {
            case DAILY -> date;
            case WEEKLY -> date.minusDays(6);
            case MONTHLY -> date.withDayOfMonth(1);
        };
    }

    private LeaderboardEntry aggregate(UUID creatorId, List<CreatorAnalytics> analytics, LeaderboardPeriod period, String category) {
        BigDecimal totalEarnings = analytics.stream()
                .map(CreatorAnalytics::getTotalEarnings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalViewers = analytics.stream()
                .mapToLong(CreatorAnalytics::getUniqueViewers)
                .sum();

        long totalSubscribers = analytics.stream()
                .mapToLong(CreatorAnalytics::getSubscriptionsCount)
                .sum();

        return LeaderboardEntry.builder()
                .creatorId(creatorId)
                .period(period)
                .category(category)
                .totalEarnings(totalEarnings)
                .totalViewers(totalViewers)
                .totalSubscribers(totalSubscribers)
                .calculatedAt(Instant.now())
                .build();
    }
}
