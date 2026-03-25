package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.dto.ChatModeRequest;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RoomController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
})
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatRoomService chatRoomService;

    @MockBean
    private UserService userService;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @Test
    void updateChatMode_ShouldSucceed() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");

        Long roomId = 101L;
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("test-room")
                .creatorId(user.getId())
                .chatMode(ChatMode.SUBSCRIBERS_ONLY)
                .build();

        ChatModeRequest request = new ChatModeRequest(ChatMode.SUBSCRIBERS_ONLY);

        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(chatRoomService.updateChatMode(eq(new java.util.UUID(0, roomId)), eq(ChatMode.SUBSCRIBERS_ONLY), eq(1L))).thenReturn(room);

        mockMvc.perform(put("/api/rooms/" + new java.util.UUID(0, roomId) + "/chat-mode")
                        .principal(() -> "creator@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatMode").value("SUBSCRIBERS_ONLY"))
                .andExpect(jsonPath("$.canChangeChatMode").value(true));
    }

    @Test
    void updateChatMode_Forbidden_ShouldReturnForbidden() throws Exception {
        User user = new User();
        user.setId(2L);
        user.setEmail("viewer@test.com");

        ChatModeRequest request = new ChatModeRequest(ChatMode.SUBSCRIBERS_ONLY);
        java.util.UUID roomId = java.util.UUID.randomUUID();

        when(userService.getByEmail("viewer@test.com")).thenReturn(user);
        when(chatRoomService.updateChatMode(any(java.util.UUID.class), any(), anyLong()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Forbidden"));

        mockMvc.perform(put("/api/rooms/" + roomId + "/chat-mode")
                        .principal(() -> "viewer@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRoom_ShouldReturnRoomDetails() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");

        Long roomId = 101L;
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("test-room")
                .creatorId(user.getId())
                .chatMode(ChatMode.PUBLIC)
                .isPrivate(false)
                .build();

        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(chatRoomService.getRoomEntity(new java.util.UUID(0, roomId))).thenReturn(room);

        mockMvc.perform(get("/api/rooms/" + new java.util.UUID(0, roomId))
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(new java.util.UUID(0, roomId).toString()))
                .andExpect(jsonPath("$.name").value("test-room"))
                .andExpect(jsonPath("$.chatMode").value("PUBLIC"))
                .andExpect(jsonPath("$.canChangeChatMode").value(true));
    }
}









