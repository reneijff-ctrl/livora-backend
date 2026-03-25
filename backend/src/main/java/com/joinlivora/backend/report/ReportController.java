package com.joinlivora.backend.report;

import com.joinlivora.backend.report.dto.ReportRequest;
import com.joinlivora.backend.report.dto.ReportResponse;
import com.joinlivora.backend.report.dto.ReportStatusUpdate;
import com.joinlivora.backend.report.model.Report;
import com.joinlivora.backend.report.model.ReportStatus;
import com.joinlivora.backend.report.service.ReportService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final ReportService reportService;
    private final UserRepository userRepository;

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse createReport(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReportRequest request
    ) {
        User reporter = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()));

        Report report = Report.builder()
                .reporterUserId(reporter.getId())
                .reportedUserId(request.getReportedUserId())
                .streamId(request.getStreamId())
                .reason(request.getReason())
                .description(request.getDescription())
                .status(ReportStatus.PENDING)
                .build();

        return mapToResponse(reportService.createReport(report));
    }

    @GetMapping("/admin/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ReportResponse>> getAllReportsAdmin(
            @RequestParam(required = false) ReportStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.getAllReports(status, pageable)
                .map(this::mapToResponse));
    }

    @GetMapping("/admin/reports/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ReportResponse getReport(@PathVariable UUID id) {
        return mapToResponse(reportService.getReport(id));
    }

    @PatchMapping("/admin/reports/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ReportResponse updateReportStatus(@PathVariable UUID id, @Valid @RequestBody ReportStatusUpdate update) {
        return mapToResponse(reportService.updateReportStatus(id, update.getStatus()));
    }

    @GetMapping("/admin/reports/reported/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReportResponse> getReportsAgainstUser(@PathVariable Long userId) {
        return reportService.getReportsAgainstUser(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/admin/reports/reporter/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReportResponse> getReportsByReporter(@PathVariable Long userId) {
        return reportService.getReportsByReporter(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private ReportResponse mapToResponse(Report report) {
        return ReportResponse.builder()
                .id(report.getId())
                .reporterUserId(report.getReporterUserId())
                .reportedUserId(report.getReportedUserId())
                .streamId(report.getStreamId())
                .reason(report.getReason())
                .description(report.getDescription())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
