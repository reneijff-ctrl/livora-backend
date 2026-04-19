package com.joinlivora.backend.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.chat.dto.ChatMessageRequest;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.websocket.StompPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private UserService userService;

    @Mock
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    private ChatController controller;

    @Mock
    private Principal principal;

    private UUID roomId;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatMessageService, userService, redisTemplate);
        lenient().when(principal.getName()).thenReturn("test@creator.com");
        roomId = UUID.randomUUID();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void deserialization_ShouldIgnoreSender() throws Exception {
        // Arrange
        String json = "{\"roomId\":\"" + roomId + "\", \"senderUsername\":\"spoofed@creator.com\", \"content\":\"Hello\"}";

        // Act
        ChatMessageDto dto = objectMapper.readValue(json, ChatMessageDto.class);

        // Assert
        assertEquals(roomId, dto.getRoomId());
        assertEquals("Hello", dto.getContent());
        assertNull(dto.getSenderUsername());
    }

    @Test
    void sendChatMessage_ShouldDelegateToService() {
        // Arrange
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("Hello");
        request.setCreatorUserId(999L);
        request.setType("CHAT");
        User user = new User();
        user.setId(123L);
        user.setEmail("test@creator.com");
        
        when(userService.resolveUserFromSubject("test@creator.com")).thenReturn(Optional.of(user));

        // Act
        controller.sendChatMessage(request, principal);

        // Assert
        verify(chatMessageService).processIncomingMessage(eq(request), eq(user));
    }

    @Test
    void joinRoom_ShouldDelegateToService() {
        // Arrange
        ChatMessageDto joinRequest = ChatMessageDto.builder()
                .creatorUserId(999L)
                .build();
        User user = new User();
        user.setId(123L);
        user.setEmail("test@creator.com");
        
        SimpMessageHeaderAccessor headerAccessor = mock(SimpMessageHeaderAccessor.class);
        when(headerAccessor.getSessionId()).thenReturn("session-123");
        when(userService.resolveUserFromSubject("test@creator.com")).thenReturn(Optional.of(user));

        // Act
        controller.joinRoom(joinRequest, principal, headerAccessor);

        // Assert
        verify(chatMessageService).processJoinRoom(eq(joinRequest), eq(user), eq("session-123"));
    }

    @Test
    void handleException_ShouldReturnErrorMap() {
        // Arrange
        Exception e = new RuntimeException("Test error");

        // Act
        Map<String, Object> result = controller.handleException(e);

        // Assert
        assertNotNull(result);
        assertEquals("RuntimeException", result.get("error"));
        assertEquals("Test error", result.get("message"));
        assertNotNull(result.get("timestamp"));
    }
}
