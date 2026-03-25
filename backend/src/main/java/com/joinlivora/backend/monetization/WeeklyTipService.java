package com.joinlivora.backend.monetization;

import com.joinlivora.backend.websocket.LiveEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyTipService {

    private final WeeklyTipLeaderboardRepository leaderboardRepository;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Registers a successful tip in the weekly leaderboard.
     * Updates both the DB for persistence and Redis for fast retrieval.
     */
    @Transactional
    public void registerTip(Long creatorId, String username, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        int week = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = now.get(IsoFields.WEEK_BASED_YEAR);

        WeeklyTipLeaderboard entry = leaderboardRepository.findByCreatorIdAndUsernameAndWeekNumberAndYear(
                creatorId, username, week, year)
                .orElseGet(() -> WeeklyTipLeaderboard.builder()
                        .creatorId(creatorId)
                        .username(username)
                        .weekNumber(week)
                        .year(year)
                        .totalAmount(BigDecimal.ZERO)
                        .build());

        entry.setTotalAmount(entry.getTotalAmount().add(amount));
        leaderboardRepository.save(entry);

        // Redis Update: leaderboard:{creatorId}:{year}:{week}
        String redisKey = String.format("leaderboard:%d:%d:%d", creatorId, year, week);
        redisTemplate.opsForZSet().add(redisKey, username, entry.getTotalAmount().doubleValue());
        
        log.info("WEEKLY-LEADERBOARD: Registered tip of {} from {} for creator {} (Week {}, Year {})",
                amount, username, creatorId, week, year);

        // Broadcast updated leaderboard to dedicated stream
        broadcastLeaderboardUpdate(creatorId);
    }

    /**
     * Broadcasts the current top 5 leaderboard to the dedicated leaderboard WebSocket topic.
     */
    private void broadcastLeaderboardUpdate(Long creatorId) {
        try {
            List<LeaderboardEntry> top5 = getTop5(creatorId);
            messagingTemplate.convertAndSend("/exchange/amq.topic/leaderboard." + creatorId,
                    LiveEvent.of("LEADERBOARD_UPDATE", top5));
        } catch (Exception e) {
            log.error("Failed to broadcast leaderboard update for creator {}", creatorId, e);
        }
    }

    /**
     * Retrieves the top 5 tippers for a creator for the current week.
     */
    public List<LeaderboardEntry> getTop5(Long creatorId) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        int week = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = now.get(IsoFields.WEEK_BASED_YEAR);

        String redisKey = String.format("leaderboard:%d:%d:%d", creatorId, year, week);
        Set<ZSetOperations.TypedTuple<String>> results = redisTemplate.opsForZSet().reverseRangeWithScores(redisKey, 0, 4);

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        return results.stream()
                .map(tuple -> new LeaderboardEntry(tuple.getValue(), tuple.getScore()))
                .collect(Collectors.toList());
    }

    /**
     * Clears all leaderboard Redis keys for a specific week.
     */
    public void clearLeaderboardForWeek(int year, int week) {
        String pattern = String.format("leaderboard:*:%d:%d", year, week);
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Cleared {} leaderboard keys from Redis for Week {}, Year {}", keys.size(), week, year);
        }
    }

    public record LeaderboardEntry(String username, Double totalAmount) {}
}
