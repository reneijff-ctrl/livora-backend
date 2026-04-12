package com.joinlivora.backend.auth.dto;

import java.time.Instant;
import java.util.List;

public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private java.time.Instant expiresAt;
    private String role;
    private Long userId;
    private String email;
    private String username;
    private String adminRole;
    private List<String> permissions;

    public LoginResponse(String accessToken, String refreshToken, java.time.Instant expiresAt, String role, Long userId, String email, String username) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.role = role;
        this.userId = userId;
        this.email = email;
        this.username = username;
    }

    public LoginResponse(String accessToken, String refreshToken, java.time.Instant expiresAt, String role, Long userId, String email, String username, String adminRole, List<String> permissions) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.role = role;
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.adminRole = adminRole;
        this.permissions = permissions;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public java.time.Instant getExpiresAt() {
        return expiresAt;
    }

    public String getRole() {
        return role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getAdminRole() {
        return adminRole;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}
