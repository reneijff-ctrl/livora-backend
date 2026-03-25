package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.streaming.dto.LivestreamAnalyticsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
public class LivestreamAnalyticsService {

    private final StringRedisTemplate redisTemplate;

    private static final String PEAK_KEY_PREFIX = "livestream:peak:";
    private static final String START_KEY_PREFIX = "livestream:start:";
    private static final String VIEWERS_KEY_PREFIX = "livestream:viewers:";

    private static final RedisScript<Void> UPDATE_STATS_SCRIPT = RedisScript.of(
            "local current = tonumber(ARGV[1]); " +
            "local peakKey = KEYS[1]; " +
            "local startKey = KEYS[2]; " +
            "local now = ARGV[2]; " +
            "local peak = redis.call('GET', peakKey); " +
            "if not peak or current > tonumber(peak) then " +
            "  redis.call('SET', peakKey, ARGV[1]); " +
            "end " +
            "redis.call('SETNX', startKey, now);", Void.class);

    public void onViewerIncrement(Long creatorUserId, long currentViewers) {
        if (creatorUserId == null) return;
        try {
            String peakKey = PEAK_KEY_PREFIX + creatorUserId;
            String startKey = START_KEY_PREFIX + creatorUserId;
            String now = String.valueOf(Instant.now().getEpochSecond());

            redisTemplate.execute(UPDATE_STATS_SCRIPT, 
                Arrays.asList(peakKey, startKey), 
                String.valueOf(currentViewers), now);
        } catch (Exception e) {
            log.error("Failed to update livestream analytics for creator {}: {}", creatorUserId, e.getMessage());
        }
    }

    public LivestreamAnalyticsResponse getCurrentStats(Long creatorUserId) {
        if (creatorUserId == null) return null;

        String viewersKey = VIEWERS_KEY_PREFIX + creatorUserId;
        String peakKey = PEAK_KEY_PREFIX + creatorUserId;
        String startKey = START_KEY_PREFIX + creatorUserId;

        Long currentCount = redisTemplate.opsForSet().size(viewersKey);
        String peakVal = redisTemplate.opsForValue().get(peakKey);
        String startVal = redisTemplate.opsForValue().get(startKey);

        long currentViewers = currentCount != null ? currentCount : 0;
        long peakViewers = parseLong(peakVal, 0);
        long startTime = parseLong(startVal, 0);

        long duration = 0;
        if (startTime > 0) {
            duration = Instant.now().getEpochSecond() - startTime;
        }

        return LivestreamAnalyticsResponse.builder()
                .currentViewers(currentViewers)
                .peakViewers(peakViewers)
                .streamDurationSeconds(duration)
                .build();
    }

    public void resetStats(Long creatorUserId) {
        if (creatorUserId == null) return;
        String viewersKey = VIEWERS_KEY_PREFIX + creatorUserId;
        String peakKey = PEAK_KEY_PREFIX + creatorUserId;
        String startKey = START_KEY_PREFIX + creatorUserId;
        redisTemplate.delete(Arrays.asList(viewersKey, peakKey, startKey));
    }

    private long parseLong(String val, long fallback) {
        if (val == null) return fallback;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
