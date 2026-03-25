package com.joinlivora.backend.content.dto;

import com.joinlivora.backend.content.AccessLevel;
import com.joinlivora.backend.content.ContentType;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ContentResponseDto implements Serializable {
    private UUID id;
    private String title;
    private String description;
    private String thumbnailUrl;
    private String mediaUrl;
    private AccessLevel accessLevel;
    private ContentType type;
    private Long creatorId;
    private String creatorEmail;
    private Integer unlockPriceTokens;
    private Instant createdAt;
    private boolean unlocked;
}
