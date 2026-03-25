package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplorePostResponse {
    private UUID postId;
    private String content;
    private Instant createdAt;
    private Long creatorId;
    private String creatorDisplayName;
    private String creatorUsername;
    private String creatorProfileImageUrl;
}
