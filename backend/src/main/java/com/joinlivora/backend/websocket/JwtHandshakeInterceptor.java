package com.joinlivora.backend.websocket;

import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final UserService userService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        
        // Capture IP and User-Agent
        String xfHeader = request.getHeaders().getFirst("X-Forwarded-For");
        String ipAddress = "unknown";
        if (xfHeader != null) {
            ipAddress = xfHeader.split(",")[0];
        } else if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            ipAddress = request.getRemoteAddress().getAddress().getHostAddress();
        }
        String userAgent = request.getHeaders().getFirst("User-Agent");
        String country = request.getHeaders().getFirst("CF-IPCountry");
        String fingerprint = request.getHeaders().getFirst("X-Device-Fingerprint");
        
        String authHeader = request.getHeaders().getFirst("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                io.jsonwebtoken.Claims claims = jwtService.validateToken(token);
                String subject = claims.getSubject();
                attributes.put("senderEmail", subject);
                attributes.put("ipAddress", ipAddress);
                attributes.put("userAgent", userAgent);
                attributes.put("country", country);
                attributes.put("fingerprint", fingerprint);
                
                com.joinlivora.backend.user.User user = userService.resolveUserFromSubject(subject)
                        .orElseThrow(() -> new AccessDeniedException("User not found for subject: " + subject));
                
                attributes.put("userId", user.getId().toString());
                log.debug("WebSocket Handshake: Authenticated user {} (ID: {})", user.getEmail(), user.getId());
                
                return true;
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                log.warn("WebSocket Handshake: JWT token expired: {}", e.getMessage());
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            } catch (Exception e) {
                log.warn("WebSocket Handshake: Invalid JWT token: {}", e.getMessage());
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            }
        } else {
            log.warn("WebSocket Handshake: No Bearer token found in Authorization header");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
