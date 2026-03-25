package com.joinlivora.backend.security.websocket;

import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.StompPrincipal;
import com.joinlivora.backend.websocket.WebSocketUserInfo;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class JwtWebSocketInterceptor implements ChannelInterceptor {
    
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtWebSocketInterceptor(
            JwtService jwtService,
            UserRepository userRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return handleConnect(message, accessor);
        }

        // For non-CONNECT messages, ensure SecurityContextHolder has the Principal if it exists in the session
        Principal principal = accessor.getUser();
        if (principal instanceof Authentication) {
            SecurityContextHolder.getContext().setAuthentication((Authentication) principal);
        }

        return message;
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("STOMP CONNECT: Missing or malformed Authorization header");
            throw new AccessDeniedException("Authentication required: Missing or malformed Authorization header");
        }

        String jwt = authHeader.substring(7);

        try {
            Claims claims = jwtService.validateToken(jwt);
            String subject = claims.getSubject();
            String role = claims.get("role", String.class);

            // Fetch user to create StompPrincipal
            User user = userRepository.findById(Long.parseLong(subject))
                    .orElseThrow(() -> new AccessDeniedException("User not found for subject: " + subject));
            String userId = user.getId().toString();

            log.debug("STOMP CONNECT: Validated JWT for user ID: {}, role: {}", subject, role);

            StompHeaderAccessor sessionAccessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

            if (sessionAccessor != null && sessionAccessor.getSessionAttributes() != null) {
                WebSocketUserInfo wsUser = new WebSocketUserInfo(
                        user.getId(),
                        user.getEmail(),
                        user.getRole().name(),
                        user.getStatus().name()
                );
                sessionAccessor.getSessionAttributes().put("ws_user", wsUser);
            }

            StompPrincipal stompPrincipal = new StompPrincipal(subject, userId);

            List<SimpleGrantedAuthority> authorities = role != null 
                    ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)) 
                    : Collections.emptyList();

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    stompPrincipal, null, authorities);

            // Set in SecurityContextHolder for the current thread
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Ensure accessor is mutable for setting creator
            if (!accessor.isMutable()) {
                accessor = StompHeaderAccessor.wrap(message);
            }

            // Set in StompHeaderAccessor to establish the Principal for the session.
            // This is crucial for SimpUserRegistry and @SendToUser functionality.
            accessor.setUser(authentication);

            log.debug("STOMP CONNECT: User {} (ID: {}) successfully authenticated and session Principal set", subject, userId);

            return org.springframework.messaging.support.MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("STOMP CONNECT: JWT token expired: {}", e.getMessage());
            throw new AccessDeniedException("Token expired");
        } catch (Exception e) {
            log.warn("STOMP CONNECT: Invalid JWT token: {}", e.getMessage());
            throw new AccessDeniedException("Invalid JWT token");
        }
    }
}
