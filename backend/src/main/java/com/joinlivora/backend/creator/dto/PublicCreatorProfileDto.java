package com.joinlivora.backend.creator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicCreatorProfileDto {
    private Long creatorId;
    private String displayName;
    private String profileImageUrl;
    private String bannerImageUrl;
    private String bio;
    private Map<String, String> socialLinks;
    private long totalFollowers;
    @JsonProperty("isOnline")
    private boolean isOnline;
}
