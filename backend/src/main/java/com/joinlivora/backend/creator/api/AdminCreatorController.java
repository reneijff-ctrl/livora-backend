package com.joinlivora.backend.creator.api;

import com.joinlivora.backend.creator.dto.AdminCreatorResponse;
import com.joinlivora.backend.creator.dto.UpdateCreatorStatusRequest;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/creators")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCreatorController {

    private final CreatorProfileService creatorProfileService;

    @GetMapping
    public ResponseEntity<Page<AdminCreatorResponse>> getCreators(
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
}
