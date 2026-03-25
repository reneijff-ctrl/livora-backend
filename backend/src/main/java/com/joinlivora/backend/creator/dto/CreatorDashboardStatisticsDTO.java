package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorDashboardStatisticsDTO {
    private long postsCount;
    private long tipsCount;
    private long subscribersCount;
}
