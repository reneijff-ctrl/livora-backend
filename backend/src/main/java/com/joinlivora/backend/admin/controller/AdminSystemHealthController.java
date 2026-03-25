package com.joinlivora.backend.admin.controller;

import com.joinlivora.backend.admin.dto.AdminSystemHealthResponse;
import com.joinlivora.backend.admin.service.AdminSystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system-health")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemHealthController {

    private final AdminSystemHealthService systemHealthService;

    @GetMapping
    public ResponseEntity<AdminSystemHealthResponse> getSystemHealth() {
        return ResponseEntity.ok(systemHealthService.getSystemHealth());
    }
}
