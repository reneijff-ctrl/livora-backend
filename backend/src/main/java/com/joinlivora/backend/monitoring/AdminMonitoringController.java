package com.joinlivora.backend.monitoring;

import com.joinlivora.backend.monitoring.dto.RevenueSummaryResponse;
import com.joinlivora.backend.monitoring.dto.SystemHealthResponse;
import com.joinlivora.backend.monitoring.dto.SystemMetricsResponse;
import com.joinlivora.backend.streaming.client.MediasoupClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMonitoringController {

    private final AdminMonitoringService monitoringService;

    @GetMapping("/health")
    public ResponseEntity<SystemHealthResponse> getHealth() {
        return ResponseEntity.ok(monitoringService.getSystemHealth());
    }

    @GetMapping("/metrics")
    public ResponseEntity<SystemMetricsResponse> getMetrics() {
        return ResponseEntity.ok(monitoringService.getSystemMetrics());
    }

    @GetMapping("/revenue-summary")
    public ResponseEntity<RevenueSummaryResponse> getRevenueSummary() {
        return ResponseEntity.ok(monitoringService.getRevenueSummary());
    }

    @GetMapping("/mediasoup/stats")
    public ResponseEntity<MediasoupClient.MediasoupStatsResponse> getMediasoupStats() {
        MediasoupClient.MediasoupStatsResponse stats =
                monitoringService.getMediasoupStats().join();
        return ResponseEntity.ok(stats);
    }
}
