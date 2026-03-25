package com.joinlivora.backend.payout.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PublicCreatorDto {
    private UUID id;
    private String username;
    private String displayName;
    private String bio;
    private String category;
    private String avatarUrl;
    private String bannerUrl;
    private boolean isLive;
}
