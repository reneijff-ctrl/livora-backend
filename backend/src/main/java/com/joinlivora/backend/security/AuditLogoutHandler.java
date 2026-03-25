package com.joinlivora.backend.security;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogoutHandler implements LogoutHandler {

    private final AuditService auditService;
    private final UserService userService;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            logLogout(userDetails.getUsername(), request);
        }
    }

    public void logLogout(String email, HttpServletRequest request) {
        if (email == null) return;
        try {
            User user = userService.getByEmail(email);
            auditService.logEvent(
                    new UUID(0L, user.getId()),
                    AuditService.USER_LOGOUT,
                    "USER",
                    new UUID(0L, user.getId()),
                    null,
                    RequestUtil.getClientIP(request),
                    RequestUtil.getUserAgent(request)
            );
        } catch (Exception e) {
            log.warn("Failed to log audit event for logout of creator: {}", email);
        }
    }
}
