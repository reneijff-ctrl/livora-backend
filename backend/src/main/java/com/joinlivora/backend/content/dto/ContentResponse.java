package com.joinlivora.backend.content.dto;

import com.joinlivora.backend.content.ContentAccessLevel;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ContentResponse {
    private UUID id;
    private String title;
    private String description;
    private String thumbnailUrl;
    private String mediaUrl; // Should be signed/limited
    private ContentAccessLevel accessLevel;
    private Long creatorId;
    private String creatorEmail;
    private Instant createdAt;
}
