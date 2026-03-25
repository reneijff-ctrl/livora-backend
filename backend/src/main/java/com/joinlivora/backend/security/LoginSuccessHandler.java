package com.joinlivora.backend.security;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler {

    private final AuditService auditService;

    public void onLoginSuccess(User user, HttpServletRequest request) {
        auditService.logEvent(
                new UUID(0L, user.getId()),
                AuditService.USER_LOGIN,
                "USER",
                new UUID(0L, user.getId()),
                null,
                RequestUtil.getClientIP(request),
                RequestUtil.getUserAgent(request)
        );
    }
}
