package com.joinlivora.backend.websocket;

import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.CookieUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String jwt = null;

                    // 1. Try Authorization header
                    List<String> authorization = accessor.getNativeHeader("Authorization");
                    if (authorization != null && !authorization.isEmpty()) {
                        String authHeader = authorization.get(0);
                        if (authHeader.startsWith("Bearer ")) {
                            jwt = authHeader.substring(7);
                        }
                    }

                    if (jwt != null && jwtService.isTokenValid(jwt)) {
                        String userEmail = jwtService.extractEmail(jwt);
                        String role = jwtService.extractRole(jwt);

                        UserDetails userDetails = new User(
                                userEmail,
                                "",
                                role != null ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)) : Collections.emptyList()
                        );

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        
                        accessor.setUser(authentication);
                        log.info("WebSocket: User {} authenticated with role {}", userEmail, role);
                    }
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    Principal principal = accessor.getUser();
                    
                    if (destination != null && destination.startsWith("/topic/rooms/") && destination.endsWith("/tips")) {
                        // Publicly accessible topic for viewing tips
                        return message;
                    }
                    
                    if ("/topic/premium".equals(destination)) {
                        if (principal == null) {
                            throw new org.springframework.security.access.AccessDeniedException("Unauthorized");
                        }
                        
                        // We need to check if user has premium access
                        // This is tricky inside an interceptor without autowiring everything.
                        // But we can extract user details from the principal.
                        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
                            boolean hasPremium = auth.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_PREMIUM") || a.getAuthority().equals("ROLE_ADMIN"));
                            
                            if (!hasPremium) {
                                log.warn("SECURITY: User {} attempted to subscribe to PREMIUM topic without access", principal.getName());
                                throw new org.springframework.security.access.AccessDeniedException("Premium access required");
                            }
                        }
                    } else if (destination != null && destination.startsWith("/topic/streams/premium/")) {
                        if (principal == null) {
                            throw new org.springframework.security.access.AccessDeniedException("Unauthorized");
                        }
                        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
                            boolean hasPremium = auth.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_PREMIUM") || a.getAuthority().equals("ROLE_ADMIN"));
                            
                            if (!hasPremium) {
                                log.warn("SECURITY: User {} attempted to subscribe to PREMIUM stream without access", principal.getName());
                                throw new org.springframework.security.access.AccessDeniedException("Premium access required");
                            }
                        }
                    } else if (destination != null && destination.startsWith("/topic/stream/") && destination.endsWith("/chat")) {
                        // Topic for stream-specific chat
                        return message;
                    }
                }
                return message;
            }
        });
    }
}
