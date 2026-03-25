package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreCreatorResponse {
    private Long userId;
    private Long creatorId;
    private String displayName;
    private String avatarUrl;
    private BigDecimal totalEarned;
    @com.fasterxml.jackson.annotation.JsonProperty("isOnline")
    private boolean isOnline;
    @com.fasterxml.jackson.annotation.JsonProperty("isLive")
    private boolean isLive;
    private long viewerCount;
    private String activeStreamThumbnailUrl;
}
