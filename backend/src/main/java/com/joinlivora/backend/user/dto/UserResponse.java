package com.joinlivora.backend.user.dto;

import com.joinlivora.backend.user.Role;

public class UserResponse {
    private Long id;
    private String email;
    private String username;
    private String displayName;
    private Role role;
    private Boolean isFollower;
    private Boolean isModerator;

    public UserResponse(Long id, String email, Role role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public UserResponse(Long id, String email, String username, String displayName, Role role) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean getIsFollower() {
        return isFollower;
    }

    public void setIsFollower(Boolean isFollower) {
        this.isFollower = isFollower;
    }

    public Boolean getIsModerator() {
        return isModerator;
    }

    public void setIsModerator(Boolean isModerator) {
        this.isModerator = isModerator;
    }
}
