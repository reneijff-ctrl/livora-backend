package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorLiveControllerTest {

    @Mock
    private StreamService LiveStreamService;

    @Mock
    private UserService userService;

    @InjectMocks
    private CreatorLiveController creatorLiveController;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");
    }

    @Test
    void getLiveStatus_ShouldReturnRoomStatus() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("creator@test.com");
        when(userService.getByEmail("creator@test.com")).thenReturn(user);

        StreamRoom room = StreamRoom.builder()
                .id(UUID.randomUUID())
                .isLive(true)
                .viewerCount(10)
                .streamTitle("Test Stream")
                .build();
        when(LiveStreamService.getCreatorRoom(user)).thenReturn(room);

        ResponseEntity<Map<String, Object>> response = creatorLiveController.getLiveStatus(principal);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertEquals(true, body.get("isLive"));
        assertEquals(10, body.get("viewerCount"));
        assertEquals("Test Stream", body.get("streamTitle"));
    }
}








