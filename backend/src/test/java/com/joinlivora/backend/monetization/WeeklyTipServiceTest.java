package com.joinlivora.backend.monetization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyTipServiceTest {

    @Mock
    private WeeklyTipLeaderboardRepository leaderboardRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private WeeklyTipService weeklyTipService;

    private Long creatorId = 1L;
    private String username = "testuser";
    private BigDecimal amount = new BigDecimal("100.00");

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void registerTip_NewEntry_ShouldCreateAndSave() {
        when(leaderboardRepository.findByCreatorIdAndUsernameAndWeekNumberAndYear(anyLong(), anyString(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());

        weeklyTipService.registerTip(creatorId, username, amount);

        verify(leaderboardRepository).save(argThat(entry -> 
                entry.getCreatorId().equals(creatorId) &&
                entry.getUsername().equals(username) &&
                entry.getTotalAmount().equals(amount)
        ));
        
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        int week = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = now.get(IsoFields.WEEK_BASED_YEAR);
        String redisKey = String.format("leaderboard:%d:%d:%d", creatorId, year, week);
        
        verify(zSetOperations).add(eq(redisKey), eq(username), eq(100.0));
    }

    @Test
    void registerTip_ExistingEntry_ShouldUpdateAndSave() {
        WeeklyTipLeaderboard existing = WeeklyTipLeaderboard.builder()
                .creatorId(creatorId)
                .username(username)
                .totalAmount(new BigDecimal("50.00"))
                .weekNumber(1)
                .year(2026)
                .build();

        when(leaderboardRepository.findByCreatorIdAndUsernameAndWeekNumberAndYear(anyLong(), anyString(), anyInt(), anyInt()))
                .thenReturn(Optional.of(existing));

        weeklyTipService.registerTip(creatorId, username, amount);

        assertEquals(new BigDecimal("150.00"), existing.getTotalAmount());
        verify(leaderboardRepository).save(existing);
        verify(zSetOperations).add(anyString(), eq(username), eq(150.0));
    }

    @Test
    void getTop5_ShouldReturnFromRedis() {
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn("user1");
        when(tuple.getScore()).thenReturn(500.0);

        when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(4L)))
                .thenReturn(Set.of(tuple));

        List<WeeklyTipService.LeaderboardEntry> result = weeklyTipService.getTop5(creatorId);

        assertEquals(1, result.size());
        assertEquals("user1", result.get(0).username());
        assertEquals(500.0, result.get(0).totalAmount());
    }

    @Test
    void clearLeaderboardForWeek_ShouldDeleteMatchingKeys() {
        int year = 2026;
        int week = 10;
        String pattern = "leaderboard:*:2026:10";
        Set<String> keys = Set.of("leaderboard:1:2026:10", "leaderboard:2:2026:10");

        when(redisTemplate.keys(pattern)).thenReturn(keys);

        weeklyTipService.clearLeaderboardForWeek(year, week);

        verify(redisTemplate).delete(keys);
    }
}








