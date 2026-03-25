package com.joinlivora.backend.audit.export;

import com.joinlivora.backend.payout.freeze.PayoutFreezeAuditService;
import com.joinlivora.backend.payout.freeze.dto.CsvExportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncExportService {

    private final PayoutFreezeAuditService auditService;
    private final ExportJobRepository exportJobRepository;

    @Async
    public void processGlobalAuditExport(Long jobId, Instant from, Instant to) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Export job not found with id: " + jobId));

        try {
            job.setStatus("PROCESSING");
            exportJobRepository.save(job);

            CsvExportResult result = auditService.generateSignedGlobalCsv(from, to);

            Path exportDir = Paths.get("exports");
            if (!Files.exists(exportDir)) {
                Files.createDirectories(exportDir);
            }

            String filename = "payout_audit_" + jobId + ".csv";
            Path filePath = exportDir.resolve(filename);
            Files.writeString(filePath, result.getCsv());

            job.setFilePath(filePath.toString());
            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());

        } catch (Exception e) {
            log.error("Failed to process global audit export for job id: {}", jobId, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
        } finally {
            exportJobRepository.save(job);
        }
    }
}
