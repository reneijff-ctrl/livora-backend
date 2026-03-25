package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicCreatorInfoResponse {
    private Long creatorId;
    private Long userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String bio;
    private String bannerUrl;
    private long followerCount;
    private long postCount;
    @com.fasterxml.jackson.annotation.JsonProperty("isOnline")
    private boolean isOnline;
    @com.fasterxml.jackson.annotation.JsonProperty("isLive")
    private boolean isLive;
    private long viewerCount;
    private String activeStreamThumbnailUrl;
    private String goalTitle;
    private Long goalTargetTokens;
    private Long goalCurrentTokens;
    @com.fasterxml.jackson.annotation.JsonProperty("isOwner")
    private boolean isOwner;
    private boolean followedByCurrentUser;
    private double rating;
    private long streamCount;
    private java.util.Map<String, String> socialLinks;

    // Profile details
    private String gender;
    private String interestedIn;
    private String languages;
    private String location;
    private String bodyType;
    private String ethnicity;
    private String eyeColor;
    private String hairColor;
    private Integer heightCm;
    private Integer weightKg;
    private java.time.LocalDate birthDate;

    // Visibility settings
    private boolean showAge;
    private boolean showLocation;
    private boolean showLanguages;
    private boolean showBodyType;
    private boolean showEthnicity;
    private boolean showHeightWeight;
}
