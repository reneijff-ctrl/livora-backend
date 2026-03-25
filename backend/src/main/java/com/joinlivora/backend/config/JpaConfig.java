package com.joinlivora.backend.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.RedisHash;

@Configuration
@EnableJpaRepositories(
	basePackages = "com.joinlivora.backend",
	excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, value = RedisHash.class)
)
public class JpaConfig {
}
