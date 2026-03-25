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
public class CreatorProfileByUsernameResponse {
    private Long creatorId;
    private String username;
    private String displayName;
    private String bio;
    private String profileImageUrl;
    private String bannerImageUrl;
    private Instant createdAt;
    private long totalPosts;
}
