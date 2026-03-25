package com.joinlivora.backend.audit.export.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportJobStatusResponse {
    private Long jobId;
    private String status;
    private Instant createdAt;
    private Instant completedAt;
    private String errorMessage;
}
