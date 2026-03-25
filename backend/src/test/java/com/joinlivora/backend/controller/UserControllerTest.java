package com.joinlivora.backend.controller;

import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.Role;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("test@example.com", "password", Role.USER);
        user.setFraudRiskLevel(FraudRiskLevel.LOW);
    }

    @Test
    void userInfo_WhenAuthenticated_ReturnsUserDetails() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("test@example.com");
        when(userService.getByEmail("test@example.com")).thenReturn(user);

        ResponseEntity<?> response = userController.userInfo(principal);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("test@example.com", body.get("email"));
        assertEquals(Role.USER, body.get("role"));
        assertEquals(FraudRiskLevel.LOW, body.get("fraudRiskLevel"));
    }

    @Test
    void userInfo_WhenNotAuthenticated_Returns401() {
        ResponseEntity<?> response = userController.userInfo(null);
        assertEquals(401, response.getStatusCode().value());
    }
}








