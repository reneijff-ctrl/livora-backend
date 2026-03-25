package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomepageCreatorDto {
    private Long userId;
    private Long creatorId;
    private String displayName;
    private String avatarUrl;
    @com.fasterxml.jackson.annotation.JsonProperty("isOnline")
    private boolean isOnline;
    @com.fasterxml.jackson.annotation.JsonProperty("isLive")
    private boolean isLive;
    private String activeStreamDescription;
    private String activeStreamThumbnailUrl;
    private String goalTitle;
    private Long goalTargetTokens;
    private Long goalCurrentTokens;
    private long viewerCount;
}
