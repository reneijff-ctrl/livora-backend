package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicCreatorListResponse {
    private Long userId;
    private Long creatorId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String bio;
    private boolean online;
    private long viewerCount;
}
