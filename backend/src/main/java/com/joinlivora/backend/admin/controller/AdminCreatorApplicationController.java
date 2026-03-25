package com.joinlivora.backend.admin.controller;

import com.joinlivora.backend.admin.dto.AdminCreatorApplicationResponse;
import com.joinlivora.backend.admin.dto.RejectApplicationRequest;
import com.joinlivora.backend.creator.model.CreatorApplication;
import com.joinlivora.backend.creator.model.CreatorApplicationStatus;
import com.joinlivora.backend.creator.repository.CreatorApplicationRepository;
import com.joinlivora.backend.creator.service.CreatorApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/creator-applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCreatorApplicationController {

    private final CreatorApplicationService creatorApplicationService;
    private final CreatorApplicationRepository creatorApplicationRepository;

    @GetMapping
    public ResponseEntity<Page<AdminCreatorApplicationResponse>> listApplications(
            @RequestParam(required = false) CreatorApplicationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<CreatorApplication> applications = creatorApplicationService.getApplications(pageable, status);
        
        return ResponseEntity.ok(applications.map(creatorApplicationService::mapToAdminDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminCreatorApplicationResponse> getApplication(@PathVariable Long id) {
        return creatorApplicationRepository.findById(id)
                .map(creatorApplicationService::mapToAdminDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approveApplication(@PathVariable Long id) {
        creatorApplicationService.approveApplicationById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> rejectApplication(@PathVariable Long id, @RequestBody RejectApplicationRequest request) {
        creatorApplicationService.rejectApplicationById(id, request.getReviewNotes());
        return ResponseEntity.ok().build();
    }

}
