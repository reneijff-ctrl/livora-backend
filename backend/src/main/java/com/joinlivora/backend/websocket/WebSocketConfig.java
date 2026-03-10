package com.joinlivora.backend.websocket;

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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Map<String, HandshakeRateLimitState> handshakeRateLimits = new ConcurrentHashMap<>();

    private List<IpAddressMatcher> proxyMatchers;

    private final WebSocketInterceptor webSocketInterceptor;
    private final JwtWebSocketInterceptor jwtWebSocketInterceptor;

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
        config.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost(brokerHost)
                .setRelayPort(brokerPort)
                .setClientLogin(brokerUsername)
                .setClientPasscode(brokerPassword)
                .setSystemLogin(brokerUsername)
                .setSystemPasscode(brokerPassword)
                .setSystemHeartbeatReceiveInterval(10000)
                .setSystemHeartbeatSendInterval(10000)
                .setTaskScheduler(heartBeatScheduler())
                .setUserDestinationBroadcast("/topic/unresolved-user-destination")
                .setUserRegistryBroadcast("/topic/log-user-registry");

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
                            
                            // Handshake rate limiting
                            long now = System.currentTimeMillis();
                            HandshakeRateLimitState state = handshakeRateLimits.compute(ip, (key, oldState) -> {
                                if (oldState == null || now - oldState.windowStartTime > 60000) {
                                    return new HandshakeRateLimitState(now);
                                } else {
                                    oldState.count++;
                                    return oldState;
                                }
                            });

                            if (state.count > maxHandshakePerMinute) {
                                log.warn("HANDSHAKE RATE LIMIT VIOLATION: ip={}, count={}, limit={}", 
                                        ip, state.count, maxHandshakePerMinute);
                                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                                return false;
                            }
                        }

                        String userAgent = request.getHeaders().getFirst("User-Agent");
                        attributes.put("userAgent", userAgent != null ? userAgent : "unknown");

                        return super.beforeHandshake(request, response, wsHandler, attributes);
                    }
                });
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(10)
                .maxPoolSize(64)
                .queueCapacity(512);
        registration.interceptors(jwtWebSocketInterceptor, webSocketInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(20)
                .maxPoolSize(256)
                .queueCapacity(1024);
    }

    @Bean
    public TaskScheduler heartBeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(8);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Scheduled(fixedRate = 600000) // Every 10 minutes
    public void cleanupRateLimits() {
        long now = System.currentTimeMillis();
        handshakeRateLimits.entrySet().removeIf(entry -> now - entry.getValue().windowStartTime > 60000);
        log.debug("Cleaned up handshake rate limits map. Current size: {}", handshakeRateLimits.size());
    }

    private static class HandshakeRateLimitState {
        final long windowStartTime;
        int count;

        HandshakeRateLimitState(long windowStartTime) {
            this.windowStartTime = windowStartTime;
            this.count = 1;
        }
    }

}
