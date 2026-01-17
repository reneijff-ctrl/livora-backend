package com.joinlivora.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.core.RedisHash;

@Configuration
@EnableJpaRepositories(basePackages = "com.joinlivora.backend")
@EnableRedisRepositories(
    basePackages = "com.joinlivora.backend",
    includeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        value = RedisHash.class
    )
)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.data.redis.repositories.enabled", havingValue = "true", matchIfMissing = true)
public class RepositoryConfig {
}
