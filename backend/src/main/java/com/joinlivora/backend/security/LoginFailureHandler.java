package com.joinlivora.backend.security;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LoginFailureHandler {

    private final AuditService auditService;

    public void onLoginFailure(User user, String reason, HttpServletRequest request) {
        UUID userId = user != null ? new UUID(0L, user.getId()) : null;
        auditService.logEvent(
                userId,
                AuditService.USER_LOGIN_FAILURE,
                "USER",
                userId,
                Map.of("type", reason),
                RequestUtil.getClientIP(request),
                RequestUtil.getUserAgent(request)
        );
    }

    public void onLoginFailure(String email, String reason, HttpServletRequest request) {
        auditService.logEvent(
                null,
                AuditService.USER_LOGIN_FAILURE,
                "USER",
                null,
                Map.of("type", reason, "email", email != null ? email : "unknown"),
                RequestUtil.getClientIP(request),
                RequestUtil.getUserAgent(request)
        );
    }
}
