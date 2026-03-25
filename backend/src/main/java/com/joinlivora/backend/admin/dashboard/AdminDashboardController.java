package com.joinlivora.backend.admin.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public ResponseEntity<AdminDashboardResponse> getDashboardData() {
        return ResponseEntity.ok(adminDashboardService.getDashboardData());
    }

    @GetMapping("/metrics")
    public ResponseEntity<AdminDashboardMetrics> getMetrics() {
        return ResponseEntity.ok(adminDashboardService.getMetrics());
    }

    @GetMapping("/charts")
    public ResponseEntity<AdminDashboardChartsDTO> getCharts() {
        return ResponseEntity.ok(adminDashboardService.getCharts());
    }
}
