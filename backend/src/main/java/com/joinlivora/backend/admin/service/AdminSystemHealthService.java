package com.joinlivora.backend.admin.service;

import com.joinlivora.backend.admin.dto.AdminSystemHealthResponse;
import com.joinlivora.backend.websocket.PresenceService;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSystemHealthService {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final PresenceService presenceService;
    private final MeterRegistry meterRegistry;

    public AdminSystemHealthResponse getSystemHealth() {
        return AdminSystemHealthResponse.builder()
                .apiLatency(getAvgApiLatency())
                .dbPoolActiveConnections(getDbActiveConnections())
                .dbPoolMaxConnections(getDbMaxConnections())
                .redisMemoryUsed(getRedisMemoryUsed())
                .activeWebSocketSessions(presenceService.getActiveSessionsCount())
                .build();
    }

    private double getAvgApiLatency() {
        try {
            Timer timer = meterRegistry.find("http.server.requests").timer();
            if (timer != null) {
                return timer.mean(TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch API latency from MeterRegistry: {}", e.getMessage());
        }
        return 0.0;
    }

    private int getDbActiveConnections() {
        try {
            if (dataSource instanceof HikariDataSource hds) {
                if (hds.getHikariPoolMXBean() != null) {
                    return hds.getHikariPoolMXBean().getActiveConnections();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch DB active connections: {}", e.getMessage());
        }
        return 0;
    }

    private int getDbMaxConnections() {
        try {
            if (dataSource instanceof HikariDataSource hds) {
                return hds.getMaximumPoolSize();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch DB max connections: {}", e.getMessage());
        }
        return 0;
    }

    private long getRedisMemoryUsed() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Properties info = connection.info("memory");
            if (info != null && info.containsKey("used_memory")) {
                Object usedMemory = info.get("used_memory");
                if (usedMemory != null) {
                    return Long.parseLong(usedMemory.toString());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Redis memory usage: {}", e.getMessage());
        }
        return 0;
    }
}
