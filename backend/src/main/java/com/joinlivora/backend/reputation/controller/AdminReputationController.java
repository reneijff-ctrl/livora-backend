package com.joinlivora.backend.reputation.controller;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.reputation.dto.ReputationAdjustmentRequest;
import com.joinlivora.backend.reputation.model.CreatorReputationSnapshot;
import com.joinlivora.backend.reputation.model.ReputationEvent;
import com.joinlivora.backend.reputation.model.ReputationEventSource;
import com.joinlivora.backend.reputation.model.ReputationEventType;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.reputation.repository.ReputationEventRepository;
import com.joinlivora.backend.reputation.service.ReputationEventService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/reputation")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminReputationController {

    private final ReputationEventService reputationEventService;
    private final ReputationEventRepository reputationEventRepository;
    private final CreatorReputationSnapshotRepository snapshotRepository;
    private final UserService userService;
    private final AuditService auditService;

    @PostMapping("/adjust/{creatorId}")
    public ResponseEntity<Void> adjustReputation(
            @PathVariable UUID creatorId,
            @RequestBody ReputationAdjustmentRequest request,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        log.info("ADMIN: Adjusting reputation for creator {}: delta={}, type={}",
                creatorId, request.getDeltaScore(), request.getReason());

        reputationEventService.recordEvent(
                creatorId,
                ReputationEventType.MANUAL_ADJUSTMENT,
                request.getDeltaScore(),
                ReputationEventSource.ADMIN,
                Map.of("type", request.getReason())
        );

        User admin = userService.getByEmail(adminDetails.getUsername());
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                AuditService.ROLE_CHANGED, // Using ROLE_CHANGED as it's a permission/standing change
                "USER",
                creatorId,
                Map.of("action", "reputation_adjustment", "delta", request.getDeltaScore(), "type", request.getReason()),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok().build();
    }

    @GetMapping("/timeline/{creatorId}")
    public ResponseEntity<List<ReputationEvent>> getTimeline(@PathVariable UUID creatorId) {
        return ResponseEntity.ok(reputationEventRepository.findAllByCreatorIdOrderByCreatedAtDesc(creatorId));
    }

    @GetMapping("/snapshot/{creatorId}")
    public ResponseEntity<CreatorReputationSnapshot> getSnapshot(@PathVariable UUID creatorId) {
        return snapshotRepository.findById(creatorId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
