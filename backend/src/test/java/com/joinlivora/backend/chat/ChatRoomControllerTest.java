package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.dto.ChatRoomDto;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatRoomController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
})
class ChatRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    void getLiveRooms_ShouldReturnList() throws Exception {
        ChatRoomDto roomDto = ChatRoomDto.builder()
                .id(UUID.randomUUID())
                .name("live-room")
                .isLive(true)
                .build();
        
        when(chatRoomService.getLiveRooms()).thenReturn(List.of(roomDto));

        mockMvc.perform(get("/api/chat/rooms/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("live-room"))
                .andExpect(jsonPath("$[0].live").value(true));
    }

    @Test
    void getRoomDto_ShouldReturnRoom() throws Exception {
        UUID roomId = UUID.randomUUID();
        ChatRoomDto roomDto = ChatRoomDto.builder()
                .id(roomId)
                .name("test-room")
                .isLive(false)
                .build();
        
        when(chatRoomService.getRoom(roomId)).thenReturn(roomDto);

        mockMvc.perform(get("/api/chat/rooms/" + roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roomId.toString()))
                .andExpect(jsonPath("$.name").value("test-room"));
    }

    @Test
    void getRoom_ShouldReturnRoom() throws Exception {
        Long roomId = 101L;
        User user = new User();
        user.setId(1L);
        user.setEmail("creatorUserId@test.com");
        
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("test-room")
                .creatorId(user.getId())
                .isPrivate(false)
                .build();
        
        when(userService.getByEmail("creatorUserId@test.com")).thenReturn(user);
        when(chatRoomService.getRoomEntity(new UUID(0, roomId))).thenReturn(room);
        when(chatRoomService.validateAccess("test-room", 1L)).thenReturn(true);

        mockMvc.perform(get("/api/chat/rooms/old/" + new UUID(0, roomId))
                        .principal(() -> "creatorUserId@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(new UUID(0, roomId).toString()))
                .andExpect(jsonPath("$.name").value("test-room"))
                .andExpect(jsonPath("$.canChangeChatMode").value(true));
    }

    @Test
    void getRoom_Unauthorized_ShouldReturnForbidden() throws Exception {
        Long roomId = 102L;
        User user = new User();
        user.setId(2L);
        user.setEmail("viewer@test.com");
        
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("private-room")
                .isPrivate(true)
                .creatorId(1L)
                .build();
        
        when(userService.getByEmail("viewer@test.com")).thenReturn(user);
        when(chatRoomService.getRoomEntity(new UUID(0, roomId))).thenReturn(room);
        when(chatRoomService.validateAccess("private-room", 2L))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Forbidden"));

        mockMvc.perform(get("/api/chat/rooms/old/" + new UUID(0, roomId))
                        .principal(() -> "viewer@test.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRoomByPpv_ShouldReturnRoom() throws Exception {
        UUID ppvId = UUID.randomUUID();
        Long roomId = 103L;
        User user = new User();
        user.setId(1L);
        user.setEmail("creatorUserId@test.com");
        
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("ppv-room")
                .creatorId(user.getId())
                .isPrivate(true)
                .build();
        
        when(userService.getByEmail("creatorUserId@test.com")).thenReturn(user);
        when(chatRoomService.getRoomByPpv(ppvId)).thenReturn(room);
        when(chatRoomService.validateAccess("ppv-room", 1L)).thenReturn(true);

        mockMvc.perform(get("/api/chat/rooms/ppv/" + ppvId)
                        .principal(() -> "creatorUserId@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate").value(true))
                .andExpect(jsonPath("$.isPpvRoom").value(true))
                .andExpect(jsonPath("$.requiresPurchase").value(true))
                .andExpect(jsonPath("$.canChangeChatMode").value(true));
    }

    @Test
    void getRoomByPpv_Unauthorized_ShouldReturnForbidden() throws Exception {
        UUID ppvId = UUID.randomUUID();
        Long roomId = 104L;
        User user = new User();
        user.setId(2L);
        user.setEmail("viewer@test.com");
        
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("ppv-room")
                .isPrivate(true)
                .creatorId(1L)
                .build();
        
        when(userService.getByEmail("viewer@test.com")).thenReturn(user);
        when(chatRoomService.getRoomByPpv(ppvId)).thenReturn(room);
        when(chatRoomService.validateAccess("ppv-room", 2L))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Purchase required"));

        mockMvc.perform(get("/api/chat/rooms/ppv/" + ppvId)
                        .principal(() -> "viewer@test.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPpvRoom_ShouldCreateSuccessfully() throws Exception {
        UUID ppvId = UUID.randomUUID();
        Long roomId = 105L;
        User user = new User();
        user.setId(1L);
        user.setEmail("creatorUserId@test.com");
        
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("ppv-" + ppvId)
                .creatorId(user.getId())
                .isPrivate(true)
                .build();

        when(userService.getByEmail("creatorUserId@test.com")).thenReturn(user);
        when(chatRoomService.createPpvChatRoom(any(UUID.class), anyLong())).thenReturn(room);

        mockMvc.perform(post("/api/chat/rooms/ppv/" + ppvId)
                        .principal(() -> "creatorUserId@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(new UUID(0, roomId).toString()))
                .andExpect(jsonPath("$.name").value("ppv-" + ppvId))
                .andExpect(jsonPath("$.canChangeChatMode").value(true));
    }

    @Test
    void createPpvRoom_Unauthorized_ShouldReturnForbidden() throws Exception {
        UUID ppvId = UUID.randomUUID();
        User user = new User();
        user.setId(2L);
        user.setEmail("not-creatorUserId@test.com");

        when(userService.getByEmail("not-creatorUserId@test.com")).thenReturn(user);
        when(chatRoomService.createPpvChatRoom(any(UUID.class), anyLong()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Only creatorUserId can manage this room"));

        mockMvc.perform(post("/api/chat/rooms/ppv/" + ppvId)
                        .principal(() -> "not-creatorUserId@test.com"))
                .andExpect(status().isForbidden());
    }
}









