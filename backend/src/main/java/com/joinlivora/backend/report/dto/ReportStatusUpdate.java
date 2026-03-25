package com.joinlivora.backend.report.dto;
import com.joinlivora.backend.report.model.ReportStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportStatusUpdate {
    @NotNull(message = "Status is required")
    private ReportStatus status;
}
