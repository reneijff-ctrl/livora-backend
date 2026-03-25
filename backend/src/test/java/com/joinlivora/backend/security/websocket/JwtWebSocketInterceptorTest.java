package com.joinlivora.backend.security.websocket;

import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.StompPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtWebSocketInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageChannel channel;

    @Mock
    private Message<byte[]> message;

    @InjectMocks
    private JwtWebSocketInterceptor interceptor;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preSend_WithValidToken_ShouldAuthenticate() {
        // Arrange
        String token = "valid-token";
        String subject = "123";
        String role = "USER";
        Long userId = 123L;

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn(subject);
        when(claims.get("role", String.class)).thenReturn(role);
        
        when(jwtService.validateToken(token)).thenReturn(claims);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        Message<?> result = interceptor.preSend(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);

        // Assert
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertTrue(principal instanceof StompPrincipal);
        StompPrincipal stompPrincipal = (StompPrincipal) principal;
        assertEquals(subject, stompPrincipal.getName());
        assertEquals(userId.toString(), stompPrincipal.getUserId());
        
        // Verify that the user was set in the accessor and reflected in the returned message
        assertTrue(result instanceof Message);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser());
        assertEquals(subject, resultAccessor.getUser().getName());

        verify(jwtService).validateToken(token);
        verify(userRepository).findById(userId);
    }

    @Test
    void preSend_WithExistingUser_ShouldSetSecurityContext() {
        // Arrange
        String email = "creator@example.com";
        StompPrincipal stompPrincipal = new StompPrincipal(email, "123");
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                stompPrincipal, null, Collections.emptyList());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setUser(authentication);

        // Act
        interceptor.preSend(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);

        // Assert
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(email, SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void preSend_WithInvalidToken_ShouldThrowException() {
        // Arrange
        String token = "invalid-token";
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        
        when(jwtService.validateToken(token)).thenThrow(new RuntimeException("Invalid token"));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> {
            interceptor.preSend(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);
        });
    }

    @Test
    void preSend_WithMissingHeader_ShouldThrowException() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

        // Act & Assert
        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> {
            interceptor.preSend(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);
        });
        assertTrue(exception.getMessage().contains("Missing or malformed Authorization header"));
    }

    @Test
    void preSend_WithMalformedHeader_ShouldThrowException() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Basic dXNlcjpwYXNz"); // Not Bearer

        // Act & Assert
        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> {
            interceptor.preSend(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);
        });
        assertTrue(exception.getMessage().contains("Missing or malformed Authorization header"));
    }

    @Test
    void preSend_WithNonConnectCommand_ShouldDoNothing() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/test");

        // Act
        Message<?> result = interceptor.preSend(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);

        // Assert
        assertNotNull(result);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
    }
}










