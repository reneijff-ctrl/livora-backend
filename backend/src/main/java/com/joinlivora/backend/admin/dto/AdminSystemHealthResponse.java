package com.joinlivora.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSystemHealthResponse {
    private double apiLatency;
    private int dbPoolActiveConnections;
    private int dbPoolMaxConnections;
    private long redisMemoryUsed;
    private long activeWebSocketSessions;
}
