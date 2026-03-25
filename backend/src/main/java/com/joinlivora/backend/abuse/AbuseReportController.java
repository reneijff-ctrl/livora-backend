package com.joinlivora.backend.abuse;

import com.joinlivora.backend.abuse.dto.ReportRequestDTO;
import com.joinlivora.backend.abuse.dto.ReportUpdateDTO;
import com.joinlivora.backend.abuse.model.AbuseReport;
import com.joinlivora.backend.abuse.model.ReportStatus;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AbuseReportController {

    private final AbuseReportService reportService;
    private final UserRepository userRepository;

    @PostMapping("/abuse-reports")
    public AbuseReport submitReport(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ReportRequestDTO request
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found"));
        return reportService.submitReport(user.getId(), request);
    }

    @GetMapping("/admin/abuse-reports")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AbuseReport> getReports(
            @RequestParam(required = false) ReportStatus status,
            Pageable pageable
    ) {
        return reportService.getReports(status, pageable);
    }

    @PatchMapping("/admin/abuse-reports/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AbuseReport updateReport(
            @PathVariable Long id,
            @RequestBody ReportUpdateDTO update
    ) {
        return reportService.updateReport(id, update);
    }
}
