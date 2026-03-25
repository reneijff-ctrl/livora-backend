package com.joinlivora.backend.creator.dto;

import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.model.ProfileVisibility;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatorProfileDTO {
    private Long id;
    private Long creatorId;
    private Long userId;
    private String displayName;
    private String username;
    private String bio;
    private String avatarUrl;
    private String bannerUrl;
    
    // Step 2 Fields
    private String realName;
    private java.time.LocalDate birthDate;
    private String gender;
    private String interestedIn;
    private String languages;
    private String location;
    private String bodyType;
    private Integer heightCm;
    private Integer weightKg;
    private String ethnicity;
    private String hairColor;
    private String eyeColor;

    private String onlyfansUrl;
    private String throneUrl;
    private String wishlistUrl;
    private String twitterUrl;
    private String instagramUrl;

    // Visibility Settings
    private boolean showAge;
    private boolean showLocation;
    private boolean showLanguages;
    private boolean showBodyType;
    private boolean showEthnicity;
    private boolean showHeightWeight;

    private ProfileStatus status;
    private ProfileVisibility visibility;
    @com.fasterxml.jackson.annotation.JsonProperty("isOnline")
    private boolean isOnline;
    @com.fasterxml.jackson.annotation.JsonProperty("isLive")
    private boolean isLive;
    private long viewerCount;
    private String activeStreamThumbnailUrl;
    private String goalTitle;
    private Long goalTargetTokens;
    private Long goalCurrentTokens;
    private Instant createdAt;

    // Phase 1: Explore card enrichment
    private long followerCount;
    private String streamTitle;

    // Phase 2: Stream metadata for explore filters/cards
    private Instant streamStartedAt;
    @com.fasterxml.jackson.annotation.JsonProperty("isPaid")
    private boolean isPaid;
    private java.math.BigDecimal admissionPrice;
    private String streamCategory;
}
