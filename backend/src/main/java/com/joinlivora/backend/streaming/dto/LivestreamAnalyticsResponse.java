package com.joinlivora.backend.streaming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LivestreamAnalyticsResponse {
    private long currentViewers;
    private long peakViewers;
    private long streamDurationSeconds;
}
