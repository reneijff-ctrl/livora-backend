package com.joinlivora.backend.auth.dto;

import java.util.List;

public class UserDto {
    private Long id;
    private String email;
    private String username;
    private String role;
    private String adminRole;
    private List<String> permissions;

    public UserDto() {}

    public UserDto(Long id, String email, String username, String role) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.role = role;
    }

    public UserDto(Long id, String email, String username, String role, String adminRole, List<String> permissions) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.role = role;
        this.adminRole = adminRole;
        this.permissions = permissions;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAdminRole() {
        return adminRole;
    }

    public void setAdminRole(String adminRole) {
        this.adminRole = adminRole;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
