package com.joinlivora.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.core.RedisHash;

@Configuration
@org.springframework.context.annotation.Profile("!dev")
@EnableRedisRepositories(
    basePackages = "com.joinlivora.backend.repository.redis",
    includeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        value = RedisHash.class
    )
)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.data.redis.repositories.enabled", havingValue = "true", matchIfMissing = true)
public class RepositoryConfig {
}
