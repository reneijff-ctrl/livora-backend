package com.joinlivora.backend.monitoring.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class SystemHealthResponse {
    private String status;
    private String version;
    private long uptime;
    private Map<String, String> components;
}
