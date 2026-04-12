package com.joinlivora.backend.auth.dto;

import com.joinlivora.backend.user.AdminRole;
import com.joinlivora.backend.user.Permission;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.UserStatus;
import com.joinlivora.backend.payment.dto.SubscriptionResponse;
import java.util.List;

public class UserMeResponse {
    private Long id;
    private String email;
    private String username;
    private String displayName;
    private Role role;
    private UserStatus status;
    private boolean emailVerified;
    private long tokenBalance;
    private SubscriptionResponse subscription;
    private com.joinlivora.backend.creator.dto.CreatorProfileDTO creatorProfile;
    private AdminRole adminRole;
    private List<String> permissions;

    public UserMeResponse() {}

    public UserMeResponse(Long id, String email, String username, String displayName, Role role, UserStatus status, 
                          boolean emailVerified, long tokenBalance, SubscriptionResponse subscription) {
        this(id, email, username, displayName, role, status, emailVerified, tokenBalance, subscription, null);
    }

    public UserMeResponse(Long id, String email, String username, String displayName, Role role, UserStatus status, 
                          boolean emailVerified, long tokenBalance, SubscriptionResponse subscription,
                          com.joinlivora.backend.creator.dto.CreatorProfileDTO creatorProfile) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.status = status;
        this.emailVerified = emailVerified;
        this.tokenBalance = tokenBalance;
        this.subscription = subscription;
        this.creatorProfile = creatorProfile;
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public long getTokenBalance() {
        return tokenBalance;
    }

    public void setTokenBalance(long tokenBalance) {
        this.tokenBalance = tokenBalance;
    }

    public SubscriptionResponse getSubscription() {
        return subscription;
    }

    public void setSubscription(SubscriptionResponse subscription) {
        this.subscription = subscription;
    }

    public com.joinlivora.backend.creator.dto.CreatorProfileDTO getCreatorProfile() {
        return creatorProfile;
    }

    public void setCreatorProfile(com.joinlivora.backend.creator.dto.CreatorProfileDTO creatorProfile) {
        this.creatorProfile = creatorProfile;
    }
    public AdminRole getAdminRole() {
        return adminRole;
    }
    public void setAdminRole(AdminRole adminRole) {
        this.adminRole = adminRole;
    }
    public List<String> getPermissions() {
        return permissions;
    }
    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
