package com.joinlivora.backend.audit.export;

import com.joinlivora.backend.audit.export.dto.ExportJobStatusResponse;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/export")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminExportController {

    private final ExportJobRepository exportJobRepository;

    @GetMapping("/{jobId}")
    public ExportJobStatusResponse getJobStatus(@PathVariable Long jobId) {
        log.info("ADMIN: Request to get export job status for id: {}", jobId);
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found with id: " + jobId));

        return ExportJobStatusResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .build();
    }
}
