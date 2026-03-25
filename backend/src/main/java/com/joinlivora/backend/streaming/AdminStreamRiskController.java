package com.joinlivora.backend.streaming;

import com.joinlivora.backend.admin.dto.StreamRiskStatusDTO;
import com.joinlivora.backend.streaming.service.StreamRiskMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/streams/risk")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminStreamRiskController {

    private final StreamRiskMonitorService streamRiskMonitorService;

    @GetMapping
    public ResponseEntity<List<StreamRiskStatusDTO>> getAllStreamRisks() {
        log.info("Fetching risk status for all active streams");
        return ResponseEntity.ok(streamRiskMonitorService.getAllStreamRisks());
    }
}
