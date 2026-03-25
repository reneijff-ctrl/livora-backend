package com.joinlivora.backend.websocket;

import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtHandshakeInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserService userService;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler wsHandler;

    @InjectMocks
    private JwtHandshakeInterceptor interceptor;

    private HttpHeaders headers;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        headers = new HttpHeaders();
        attributes = new HashMap<>();
        when(request.getHeaders()).thenReturn(headers);
    }

    @Test
    void beforeHandshake_WithValidToken_ShouldReturnTrueAndPopulateAttributes() throws Exception {
        String token = "valid.token.here";
        String subject = "123";
        headers.set("Authorization", "Bearer " + token);
        
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn(subject);
        when(jwtService.validateToken(token)).thenReturn(claims);
        
        User user = new User();
        user.setId(123L);
        user.setEmail("creator@example.com");
        when(userService.resolveUserFromSubject(subject)).thenReturn(Optional.of(user));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertTrue(result);
        assertEquals("creator@example.com", attributes.get("senderEmail"));
        assertEquals("123", attributes.get("creator"));
    }

    @Test
    void beforeHandshake_WithInvalidToken_ShouldReturnFalse() throws Exception {
        String token = "invalid.token.here";
        headers.set("Authorization", "Bearer " + token);
        
        when(jwtService.validateToken(token)).thenThrow(new RuntimeException("Invalid token"));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertFalse(result);
        assertTrue(attributes.isEmpty());
    }

    @Test
    void beforeHandshake_WithMissingToken_ShouldReturnFalse() throws Exception {
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertFalse(result);
        assertTrue(attributes.isEmpty());
    }
}








