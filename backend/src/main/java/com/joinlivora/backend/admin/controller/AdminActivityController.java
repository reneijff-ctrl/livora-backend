package com.joinlivora.backend.admin.controller;

import com.joinlivora.backend.admin.dto.AdminActivityEventDTO;
import com.joinlivora.backend.admin.service.AdminActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/activity")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminActivityController {

    private final AdminActivityService adminActivityService;

    @GetMapping
    public List<AdminActivityEventDTO> getRecentActivity() {
        try {
            return adminActivityService.getRecentActivity();
        } catch (Exception e) {
            // Return empty list on failure as per safety requirements
            return new java.util.ArrayList<>();
        }
    }
}
