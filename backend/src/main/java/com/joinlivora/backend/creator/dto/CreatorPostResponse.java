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
public class CreatorPostResponse {
    private UUID id;
    private Long creatorId;
    private String displayName;
    private String username;
    private String avatarUrl;
    private String title;
    private String content;
    private Instant createdAt;
    private long likeCount;
    private boolean likedByMe;
}
