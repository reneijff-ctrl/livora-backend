package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorDTO {
    private Long id;
    private String displayName;
    private String bio;
    private String avatarUrl;
    private long followersCount;
    private long postCount;
    private java.math.BigDecimal totalTips;
    private java.math.BigDecimal totalEarnings;
}
