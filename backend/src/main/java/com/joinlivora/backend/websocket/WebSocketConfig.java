package com.joinlivora.backend.websocket;

import com.joinlivora.backend.config.MetricsService;
import com.joinlivora.backend.security.websocket.JwtWebSocketInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @org.springframework.beans.factory.annotation.Value("${livora.security.cors.allowed-origins}")
    private java.util.List<String> allowedOrigins;

    @org.springframework.beans.factory.annotation.Value("${livora.security.trusted-proxies:}")
    private java.util.List<String> trustedProxies;

    @org.springframework.beans.factory.annotation.Value("${livora.security.max-handshake-per-minute:20}")
    private int maxHandshakePerMinute;

    @org.springframework.beans.factory.annotation.Value("${livora.websocket.broker.host:localhost}")
    private String brokerHost;

    @org.springframework.beans.factory.annotation.Value("${livora.websocket.broker.port:61613}")
    private int brokerPort;

    @org.springframework.beans.factory.annotation.Value("${livora.websocket.broker.username:guest}")
    private String brokerUsername;

    @org.springframework.beans.factory.annotation.Value("${livora.websocket.broker.password:guest}")
    private String brokerPassword;

    private List<IpAddressMatcher> proxyMatchers;

    /**
     * Atomic Lua script: INCR the key, then set EXPIRE only on the very first increment.
     * This eliminates the race window between INCR and a separate EXPIRE call.
     * Returns the post-increment count as a Long.
     */
    private static final RedisScript<Long> INCR_EXPIRE_SCRIPT = RedisScript.of(
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return v",
            Long.class
    );

    private final WebSocketInterceptor webSocketInterceptor;
    private final JwtWebSocketInterceptor jwtWebSocketInterceptor;
    private final StringRedisTemplate redisTemplate;
    private final MetricsService metricsService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.joinlivora.backend.resilience.RedisCircuitBreakerService redisCircuitBreaker;

    @jakarta.annotation.PostConstruct
    public void initProxyMatchers() {
        this.proxyMatchers = new ArrayList<>();
        if (trustedProxies != null) {
            for (String proxy : trustedProxies) {
                if (proxy != null && !proxy.trim().isEmpty()) {
                    proxyMatchers.add(new IpAddressMatcher(proxy.trim()));
                }
            }
        }
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Broadcast topics: /topic/chat/{creatorUserId}, /topic/webrtc/{creatorUserId}
        // Use STOMP Broker Relay (RabbitMQ/Redis) instead of SimpleBroker for horizontal scaling
        config.enableStompBrokerRelay("/queue", "/exchange")
                .setRelayHost(brokerHost)
                .setRelayPort(brokerPort)
                .setClientLogin(brokerUsername)
                .setClientPasscode(brokerPassword)
                .setSystemLogin(brokerUsername)
                .setSystemPasscode(brokerPassword)
                .setSystemHeartbeatReceiveInterval(10000)
                .setSystemHeartbeatSendInterval(10000)
                .setTaskScheduler(heartBeatScheduler())
                .setUserDestinationBroadcast("/exchange/amq.topic/unresolved-user-destination")
                .setUserRegistryBroadcast("/exchange/amq.topic/log-user-registry");

        // Client-to-server messages: /app/chat.send, /app/webrtc.signal
        config.setApplicationDestinationPrefixes("/app");

        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(128 * 1024)        // 128 KB
                .setSendBufferSizeLimit(1024 * 1024)      // 1 MB per session
                .setSendTimeLimit(20000);                 // 20 seconds
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.toArray(new String[0]))
                .addInterceptors(new HttpSessionHandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
                        String remoteIp = null;
                        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
                            remoteIp = request.getRemoteAddress().getAddress().getHostAddress();
                        }

                        String ip = null;
                        boolean fromTrustedProxy = false;
                        if (remoteIp != null) {
                            for (IpAddressMatcher matcher : proxyMatchers) {
                                if (matcher.matches(remoteIp)) {
                                    fromTrustedProxy = true;
                                    break;
                                }
                            }
                        }

                        if (fromTrustedProxy) {
                            String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
                            if (forwarded != null && !forwarded.isEmpty()) {
                                ip = forwarded.split(",")[0].trim();
                            }
                        }

                        if (ip == null) {
                            ip = remoteIp;
                        }

                        if (ip != null) {
                            attributes.put("ip", ip);

                            // Handshake rate limiting — atomic Lua INCR+EXPIRE (cluster-safe, race-free).
                            // Uses execute() so the circuit breaker can transition to HALF_OPEN and
                            // probe Redis recovery — the old isOpen() pre-check blocked that probe.
                            String rateLimitKey = "ws:handshake:rate:" + ip;
                            final String finalIp = ip;
                            Long count = (redisCircuitBreaker != null)
                                    ? redisCircuitBreaker.execute(
                                            () -> redisTemplate.execute(
                                                    INCR_EXPIRE_SCRIPT,
                                                    Collections.singletonList(rateLimitKey),
                                                    "60"),
                                            null,
                                            "redis:ws:handshake-rate:" + finalIp)
                                    : redisTemplate.execute(
                                            INCR_EXPIRE_SCRIPT,
                                            Collections.singletonList(rateLimitKey),
                                            "60");
                            if (count == null) {
                                log.warn("WS HANDSHAKE RATE LIMIT: Redis unavailable for ip={}, failing open", ip);
                                if (metricsService != null) metricsService.getRedisFailuresTotal().increment();
                            }

                            if (count != null && count > maxHandshakePerMinute) {
                                log.warn("WS HANDSHAKE RATE LIMIT EXCEEDED: ip={}, count={}, limit={}",
                                        ip, count, maxHandshakePerMinute);
                                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                                return false;
                            }
                        }

                        String userAgent = request.getHeaders().getFirst("User-Agent");
                        attributes.put("userAgent", userAgent != null ? userAgent : "unknown");

                        return super.beforeHandshake(request, response, wsHandler, attributes);
                    }
                })
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(20)
                .maxPoolSize(128)
                .queueCapacity(2048);
        registration.interceptors(jwtWebSocketInterceptor, webSocketInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(20)
                .maxPoolSize(256)
                .queueCapacity(4096);
    }


    @Bean
    public TaskScheduler heartBeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(8);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

}
