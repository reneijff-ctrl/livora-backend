package com.joinlivora.backend.user.dto;

import com.joinlivora.backend.user.Role;

public class CreatorMeResponse {
    private Long id;
    private String email;
    private Role role;
    private int trustScore;
    private boolean payoutsEnabled;

    public CreatorMeResponse() {}

    public CreatorMeResponse(Long id, String email, Role role, int trustScore, boolean payoutsEnabled) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.trustScore = trustScore;
        this.payoutsEnabled = payoutsEnabled;
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

    public int getTrustScore() {
        return trustScore;
    }

    public void setTrustScore(int trustScore) {
        this.trustScore = trustScore;
    }

    public boolean isPayoutsEnabled() {
        return payoutsEnabled;
    }

    public void setPayoutsEnabled(boolean payoutsEnabled) {
        this.payoutsEnabled = payoutsEnabled;
    }
}
