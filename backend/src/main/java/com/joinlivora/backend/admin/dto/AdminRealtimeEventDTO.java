package com.joinlivora.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRealtimeEventDTO {
    private String type;
    private String eventType;
    private String message;
    private Instant timestamp;
    private Long userId;
    private String streamId;
    private String severity;
    private Map<String, Object> metadata;
}
