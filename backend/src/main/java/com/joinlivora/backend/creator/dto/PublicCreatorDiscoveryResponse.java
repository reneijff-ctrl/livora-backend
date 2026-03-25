package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicCreatorDiscoveryResponse {
    private Long userId;
    private Long creatorId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String shortBio;
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
}
