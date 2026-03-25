package com.joinlivora.backend.payout.freeze;

import com.joinlivora.backend.audit.export.AsyncExportService;
import com.joinlivora.backend.audit.export.ExportJob;
import com.joinlivora.backend.audit.export.ExportJobRepository;
import com.joinlivora.backend.payout.freeze.dto.CsvExportResult;
import com.joinlivora.backend.payout.freeze.dto.PayoutFreezeRequest;
import com.joinlivora.backend.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/payout-freeze")
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminPayoutFreezeController {

    private final PayoutFreezeService payoutFreezeService;
    private final PayoutFreezeAuditService auditService;
    private final AsyncExportService asyncExportService;
    private final ExportJobRepository exportJobRepository;

    public AdminPayoutFreezeController(
            @org.springframework.beans.factory.annotation.Qualifier("payoutFreezeServiceNew") PayoutFreezeService payoutFreezeService,
            PayoutFreezeAuditService auditService,
            AsyncExportService asyncExportService,
            ExportJobRepository exportJobRepository) {
        this.payoutFreezeService = payoutFreezeService;
        this.auditService = auditService;
        this.asyncExportService = asyncExportService;
        this.exportJobRepository = exportJobRepository;
    }

    @PostMapping("/freeze")
    public ResponseEntity<?> freezeCreator(@RequestBody PayoutFreezeRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        log.info("ADMIN: Request to freeze payouts for creator {} by admin {}", request.getCreatorId(), principal.getUserId());
        payoutFreezeService.freezeCreator(request.getCreatorId(), request.getReason(), principal.getUserId());
        return ResponseEntity.ok(Map.of("message", "Payouts frozen for creator " + request.getCreatorId()));
    }

    @PostMapping("/unfreeze/{creatorId}")
    public ResponseEntity<?> unfreezeCreator(@PathVariable Long creatorId,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        log.info("ADMIN: Request to unfreeze payouts for creator {} by admin {}", creatorId, principal.getUserId());
        payoutFreezeService.unfreezeCreator(creatorId);
        return ResponseEntity.ok(Map.of("message", "Payouts unfrozen for creator " + creatorId));
    }

    @GetMapping("/audit/{creatorId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PayoutFreezeAuditLog> getAuditLogs(@PathVariable Long creatorId) {
        log.info("ADMIN: Request to get payout freeze audit logs for creator {}", creatorId);
        return auditService.getAuditForCreator(creatorId);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<PayoutFreezeAuditLog> getAllAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("ADMIN: Request to get all payout freeze audit logs (page: {}, size: {})", page, size);
        return auditService.getAllAudit(PageRequest.of(page, size));
    }

    @GetMapping("/audit/{creatorId}/export")
    public ResponseEntity<String> exportAuditLogs(
            @PathVariable Long creatorId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        log.info("ADMIN: Request to export payout freeze audit logs for creator {} [from: {}, to: {}]", creatorId, from, to);
        String csv = auditService.generateCsvForCreator(creatorId, from, to);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payout_freeze_audit_" + creatorId + ".csv")
                .body(csv);
    }

    @GetMapping("/audit/export")
    public ResponseEntity<String> exportGlobalAuditLogs(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        log.info("ADMIN: Request to export GLOBAL payout freeze audit logs [from: {}, to: {}]", from, to);
        CsvExportResult result = auditService.generateSignedGlobalCsv(from, to);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payout_audit_global.csv")
                .header("X-File-SHA256", result.getSha256Hash())
                .body(result.getCsv());
    }

    @PostMapping("/audit/export/async")
    public ResponseEntity<?> exportGlobalAuditLogsAsync(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        log.info("ADMIN: Request to export GLOBAL payout freeze audit logs asynchronously [from: {}, to: {}]", from, to);

        ExportJob job = ExportJob.builder()
                .type("PAYOUT_AUDIT")
                .status("PENDING")
                .createdAt(Instant.now())
                .requestedByAdminId(1L) // temporary
                .build();

        job = exportJobRepository.save(job);

        asyncExportService.processGlobalAuditExport(job.getId(), from, to);

        return ResponseEntity.ok(Map.of(
                "jobId", job.getId(),
                "status", "PENDING"
        ));
    }
}
