package com.joinlivora.backend.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemMetricsResponse {
    private long totalUsers;
    private long activeStreams;
    private long totalPayments;
    private double cpuLoad;
    private long usedMemoryBytes;
    private long totalMemoryBytes;
}
