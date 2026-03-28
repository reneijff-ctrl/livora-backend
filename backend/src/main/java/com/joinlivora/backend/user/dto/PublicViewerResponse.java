package com.joinlivora.backend.user.dto;

public class PublicViewerResponse {
    private Long id;
    private String username;
    private String displayName;
    private Boolean isFollower;
    private Boolean isModerator;

    public PublicViewerResponse(Long id, String username, String displayName) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
    }

    public PublicViewerResponse(Long id, String username, String displayName, Boolean isFollower) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.isFollower = isFollower;
    }

    public PublicViewerResponse(Long id, String username, String displayName, Boolean isFollower, Boolean isModerator) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.isFollower = isFollower;
        this.isModerator = isModerator;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
