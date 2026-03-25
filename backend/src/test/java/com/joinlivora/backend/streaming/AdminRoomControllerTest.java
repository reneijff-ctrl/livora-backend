package com.joinlivora.backend.streaming;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Disabled("Legacy streaming architecture")
@ExtendWith(MockitoExtension.class)
class AdminRoomControllerTest {

    @Mock
    private StreamService LiveStreamService;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    @Mock
    private UserDetails adminDetails;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AdminRoomController controller;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@test.com");
        admin.setRole(Role.ADMIN);
    }

    @Test
    void getActiveRooms_ShouldReturnData() {
        StreamRoom room = new StreamRoom();
        when(LiveStreamService.getActiveRooms()).thenReturn(List.of(room));

        ResponseEntity<List<StreamRoom>> response = controller.getActiveRooms();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void closeRoom_ShouldCallService() {
        UUID roomId = UUID.randomUUID();
        when(adminDetails.getUsername()).thenReturn("admin@test.com");
        when(userService.getByEmail("admin@test.com")).thenReturn(admin);

        ResponseEntity<Void> response = controller.closeRoom(roomId, adminDetails, httpRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(LiveStreamService).closeRoom(roomId);
        verify(auditService).logEvent(any(), eq(AuditService.CONTENT_TAKEDOWN), eq("STREAM_ROOM"), eq(roomId), any(), any(), any());
    }

    @Test
    void enableSlowMode_ShouldCallService() {
        UUID roomId = UUID.randomUUID();
        when(adminDetails.getUsername()).thenReturn("admin@test.com");
        when(userService.getByEmail("admin@test.com")).thenReturn(admin);

        ResponseEntity<Void> response = controller.enableSlowMode(roomId, adminDetails, httpRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(LiveStreamService).setSlowMode(roomId, true);
        verify(auditService).logEvent(any(), eq(AuditService.ROOM_MODERATION), eq("STREAM_ROOM"), eq(roomId), any(), any(), any());
    }

    @Test
    void disableSlowMode_ShouldCallService() {
        UUID roomId = UUID.randomUUID();
        when(adminDetails.getUsername()).thenReturn("admin@test.com");
        when(userService.getByEmail("admin@test.com")).thenReturn(admin);

        ResponseEntity<Void> response = controller.disableSlowMode(roomId, adminDetails, httpRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(LiveStreamService).setSlowMode(roomId, false);
        verify(auditService).logEvent(any(), eq(AuditService.ROOM_MODERATION), eq("STREAM_ROOM"), eq(roomId), any(), any(), any());
    }
}









