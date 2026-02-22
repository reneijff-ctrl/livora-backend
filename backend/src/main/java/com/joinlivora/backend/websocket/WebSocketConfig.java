package com.joinlivora.backend.websocket;

import com.joinlivora.backend.security.websocket.JwtWebSocketInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @org.springframework.beans.factory.annotation.Value("${livora.security.cors.allowed-origins}")
    private java.util.List<String> allowedOrigins;

    private final WebSocketInterceptor webSocketInterceptor;
    private final JwtWebSocketInterceptor jwtWebSocketInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Broadcast topics: /topic/chat/{roomId}, /topic/webrtc/{creator}
        config.enableSimpleBroker("/topic", "/queue");

        // Client-to-server messages: /app/chat.send, /app/webrtc.signal
        config.setApplicationDestinationPrefixes("/app");

        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.toArray(new String[0]));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtWebSocketInterceptor, webSocketInterceptor);
    }

}
