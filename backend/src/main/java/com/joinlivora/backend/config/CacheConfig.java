package com.joinlivora.backend.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return (builder) -> builder
                .withCacheConfiguration("users",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("subscriptions",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(1)))
                .withCacheConfiguration("creatorEarnings",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(30)))
                .withCacheConfiguration("ipReputation",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration("publicContent",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("adminDashboardData",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(15)))
                .withCacheConfiguration("adminDashboardMetrics",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(15)))
                .withCacheConfiguration("adminActivity",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(15)))
                .withCacheConfiguration("adminDashboardCharts",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(1)))
                .withCacheConfiguration("adminDashboardPayoutVolume",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("viewer_access",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(30)))
                .withCacheConfiguration("active_streams",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(30)))
                .withCacheConfiguration("streams",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(30)))
                .withCacheConfiguration("adminDashboardFreezesPerDay",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)));
    }
}
