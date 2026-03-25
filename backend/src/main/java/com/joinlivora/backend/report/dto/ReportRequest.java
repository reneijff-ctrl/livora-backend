package com.joinlivora.backend.report.dto;

import com.joinlivora.backend.report.model.ReportReason;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {
    @NotNull(message = "Reported user ID is required")
    private Long reportedUserId;

    private UUID streamId;

    @NotNull(message = "Report reason is required")
    private ReportReason reason;

    private String description;
}
