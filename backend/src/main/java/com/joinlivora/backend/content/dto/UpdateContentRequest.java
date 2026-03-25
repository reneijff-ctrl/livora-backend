package com.joinlivora.backend.content.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateContentRequest {
    private String title;
    private String description;
    private String accessLevel;
    private Integer unlockPriceTokens;
}
