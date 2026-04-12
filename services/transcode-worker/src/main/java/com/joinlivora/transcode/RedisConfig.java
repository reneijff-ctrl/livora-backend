package com.joinlivora.transcode;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Configures the Redis Pub/Sub listener infrastructure for the transcode worker.
 *
 * <p>The {@link RedisMessageListenerContainer} is the Spring-managed thread that
 * drives all Pub/Sub subscriptions. {@link TranscodeWorkerService} registers its
 * stop-signal listener on this container via
 * {@code container.addMessageListener(listener, topic)} at startup.
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a bare {@link RedisMessageListenerContainer}.
     * Listeners are added dynamically by {@link TranscodeWorkerService} so that
     * the {@link TranscodeProperties} topic name is respected at runtime.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Use a named thread for easier debugging in thread dumps
        container.setTaskExecutor(r -> {
            Thread t = new Thread(r, "redis-pubsub-listener");
            t.setDaemon(true);
            return t;
        });

        return container;
    }
}
