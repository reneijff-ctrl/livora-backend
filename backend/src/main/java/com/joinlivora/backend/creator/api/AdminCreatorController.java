package com.joinlivora.backend.creator.api;

import com.joinlivora.backend.admin.dto.AdminCreatorDto;
import com.joinlivora.backend.admin.service.UnifiedAdminCreatorService;
import com.joinlivora.backend.creator.dto.AdminCreatorResponse;
import com.joinlivora.backend.creator.dto.UpdateCreatorStatusRequest;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/creators")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCreatorController {

    private final CreatorProfileService creatorProfileService;
    private final UnifiedAdminCreatorService unifiedAdminCreatorService;

    // -------------------------------------------------------------------------
    // Legacy endpoint — kept for backward compatibility
    // -------------------------------------------------------------------------

    @GetMapping("/legacy")
    public ResponseEntity<Page<AdminCreatorResponse>> getCreatorsLegacy(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(creatorProfileService.getAdminCreators(pageable));
    }

    @GetMapping("/stripe-status")
    public ResponseEntity<Page<com.joinlivora.backend.creator.dto.AdminCreatorStripeStatusResponse>> getCreatorsStripeStatus(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(creatorProfileService.getAdminCreatorsStripeStatus(pageable));
    }

    @PostMapping("/{userId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long userId,
            @RequestBody UpdateCreatorStatusRequest request
    ) {
        creatorProfileService.updateCreatorStatus(userId, request.getStatus());
        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------------------------
    // Unified directory — paginated, filterable, searchable
    // -------------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<Page<AdminCreatorDto>> getCreators(
            @RequestParam(required = false) ProfileStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(unifiedAdminCreatorService.getCreators(status, search, pageable));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminCreatorDto> getCreator(@PathVariable Long userId) {
        return ResponseEntity.ok(unifiedAdminCreatorService.getCreator(userId));
    }

    // -------------------------------------------------------------------------
    // Queue endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/queue/applications")
    public ResponseEntity<List<AdminCreatorDto>> getApplicationQueue() {
        return ResponseEntity.ok(unifiedAdminCreatorService.getApplicationQueue());
    }

    @GetMapping("/queue/verifications")
    public ResponseEntity<List<AdminCreatorDto>> getVerificationQueue() {
        return ResponseEntity.ok(unifiedAdminCreatorService.getVerificationQueue());
    }

    // -------------------------------------------------------------------------
    // Lifecycle actions — all keyed by userId
    // -------------------------------------------------------------------------

    @PostMapping("/{userId}/approve-application")
    public ResponseEntity<Void> approveApplication(@PathVariable Long userId) {
        unifiedAdminCreatorService.approveApplication(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/reject-application")
    public ResponseEntity<Void> rejectApplication(
            @PathVariable Long userId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        unifiedAdminCreatorService.rejectApplication(userId, reason);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/approve-verification")
    public ResponseEntity<Void> approveVerification(@PathVariable Long userId) {
        unifiedAdminCreatorService.approveVerification(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/reject-verification")
    public ResponseEntity<Void> rejectVerification(
            @PathVariable Long userId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        unifiedAdminCreatorService.rejectVerification(userId, reason);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/suspend")
    public ResponseEntity<Void> suspend(
            @PathVariable Long userId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        unifiedAdminCreatorService.suspend(userId, reason);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/unsuspend")
    public ResponseEntity<Void> unsuspend(@PathVariable Long userId) {
        unifiedAdminCreatorService.unsuspend(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long userId) {
        unifiedAdminCreatorService.approveCreator(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long userId) {
        unifiedAdminCreatorService.rejectCreator(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long userId) {
        unifiedAdminCreatorService.activateCreator(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/suspend-profile")
    public ResponseEntity<Void> suspendProfile(
            @PathVariable Long userId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        unifiedAdminCreatorService.suspendCreator(userId, reason);
        return ResponseEntity.ok().build();
    }
}
