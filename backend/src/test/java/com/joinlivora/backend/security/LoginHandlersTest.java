package com.joinlivora.backend.security;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginHandlersTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private LoginSuccessHandler successHandler;

    @InjectMocks
    private LoginFailureHandler failureHandler;

    @InjectMocks
    private AuditLogoutHandler logoutHandler;

    @Mock
    private com.joinlivora.backend.user.UserService userService;

    @Mock
    private HttpServletRequest request;

    @Test
    void logoutHandler_ShouldLogEvent() {
        User user = new User();
        user.setId(123L);
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        lenient().when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        lenient().when(request.getHeader(anyString())).thenReturn(null);
        lenient().when(request.getHeader("User-Agent")).thenReturn("Mozilla");

        logoutHandler.logLogout("test@test.com", request);

        verify(auditService).logEvent(
                eq(new UUID(0L, 123L)),
                eq(AuditService.USER_LOGOUT),
                eq("USER"),
                eq(new UUID(0L, 123L)),
                isNull(),
                eq("1.2.3.4"),
                eq("Mozilla")
        );
    }

    @Test
    void successHandler_ShouldLogEvent() {
        User user = new User();
        user.setId(123L);
        lenient().when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        lenient().when(request.getHeader(anyString())).thenReturn(null);
        lenient().when(request.getHeader("User-Agent")).thenReturn("Mozilla");

        successHandler.onLoginSuccess(user, request);

        verify(auditService).logEvent(
                eq(new UUID(0L, 123L)),
                eq(AuditService.USER_LOGIN),
                eq("USER"),
                eq(new UUID(0L, 123L)),
                isNull(),
                eq("1.2.3.4"),
                eq("Mozilla")
        );
    }

    @Test
    void failureHandler_WithUser_ShouldLogEvent() {
        User user = new User();
        user.setId(123L);
        lenient().when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        lenient().when(request.getHeader(anyString())).thenReturn(null);
        lenient().when(request.getHeader("User-Agent")).thenReturn("Mozilla");

        failureHandler.onLoginFailure(user, "bad_password", request);

        verify(auditService).logEvent(
                eq(new UUID(0L, 123L)),
                eq(AuditService.USER_LOGIN_FAILURE),
                eq("USER"),
                eq(new UUID(0L, 123L)),
                any(),
                eq("1.2.3.4"),
                eq("Mozilla")
        );
    }

    @Test
    void failureHandler_WithoutUser_ShouldLogEvent() {
        lenient().when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        lenient().when(request.getHeader(anyString())).thenReturn(null);
        lenient().when(request.getHeader("User-Agent")).thenReturn("Mozilla");

        failureHandler.onLoginFailure("test@test.com", "not_found", request);

        verify(auditService).logEvent(
                isNull(),
                eq(AuditService.USER_LOGIN_FAILURE),
                eq("USER"),
                isNull(),
                any(),
                eq("1.2.3.4"),
                eq("Mozilla")
        );
    }
}








