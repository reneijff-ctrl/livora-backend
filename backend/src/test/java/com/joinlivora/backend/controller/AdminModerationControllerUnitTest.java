package com.joinlivora.backend.controller;

import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.user.dto.ModerationActionRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModerationControllerUnitTest {

    @Mock
    private UserService userService;

    @Mock
    private UserDetails adminDetails;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AdminModerationController controller;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@test.com");
        admin.setRole(Role.ADMIN);

        lenient().when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent");
    }

    @Test
    void getUsers_ShouldReturnUsers() {
        Page<User> page = new PageImpl<>(List.of(new User()));
        when(userService.getAllUsers(any())).thenReturn(page);

        ResponseEntity<Page<User>> response = controller.getUsers(PageRequest.of(0, 20));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void getCreators_ShouldReturnCreators() {
        Page<User> page = new PageImpl<>(List.of(new User()));
        when(userService.getAllCreators(any())).thenReturn(page);

        ResponseEntity<Page<User>> response = controller.getCreators(PageRequest.of(0, 20));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void suspendUser_ShouldCallService() {
        when(adminDetails.getUsername()).thenReturn("admin@test.com");
        when(userService.getByEmail("admin@test.com")).thenReturn(admin);

        ModerationActionRequest request = new ModerationActionRequest("Violation");
        ResponseEntity<Void> response = controller.suspendUser(2L, request, adminDetails, httpRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(userService).suspendUser(eq(2L), eq(admin), eq("Violation"), eq("127.0.0.1"), eq("TestAgent"));
    }

    @Test
    void unsuspendUser_ShouldCallService() {
        when(adminDetails.getUsername()).thenReturn("admin@test.com");
        when(userService.getByEmail("admin@test.com")).thenReturn(admin);

        ModerationActionRequest request = new ModerationActionRequest("Clean record");
        ResponseEntity<Void> response = controller.unsuspendUser(2L, request, adminDetails, httpRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(userService).unsuspendUser(eq(2L), eq(admin), eq("Clean record"), eq("127.0.0.1"), eq("TestAgent"));
    }

    @Test
    void shadowbanCreator_ShouldCallService() {
        UUID profileId = UUID.randomUUID();
        when(adminDetails.getUsername()).thenReturn("admin@test.com");
        when(userService.getByEmail("admin@test.com")).thenReturn(admin);

        ModerationActionRequest request = new ModerationActionRequest("Spam");
        ResponseEntity<Void> response = controller.shadowbanCreator(profileId, request, adminDetails, httpRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(userService).shadowbanCreator(eq(profileId), eq(admin), eq("Spam"), eq("127.0.0.1"), eq("TestAgent"));
    }
}








