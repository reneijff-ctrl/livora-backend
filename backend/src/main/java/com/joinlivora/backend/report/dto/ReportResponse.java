package com.joinlivora.backend.report.dto;

import com.joinlivora.backend.report.model.ReportReason;
import com.joinlivora.backend.report.model.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private UUID id;
    private Long reporterUserId;
    private Long reportedUserId;
    private UUID streamId;
    private ReportReason reason;
    private String description;
    private ReportStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
