package com.joinlivora.backend.audit.controller;

import com.joinlivora.backend.audit.model.AuditLog;
import com.joinlivora.backend.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditRecentController {

    private static final List<String> CREATOR_ACTIONS = List.of(
            "CREATOR_APPROVED",
            "CREATOR_REJECTED",
            "CREATOR_SUSPENDED",
            "CREATOR_UNSUSPENDED",
            "CREATOR_APPLICATION_APPROVED",
            "CREATOR_APPLICATION_REJECTED",
            "CREATOR_VERIFICATION_APPROVED",
            "CREATOR_VERIFICATION_REJECTED"
    );

    private static final Map<String, String> ACTION_LABELS = Map.of(
            "CREATOR_APPROVED", "approved creator",
            "CREATOR_REJECTED", "rejected creator",
            "CREATOR_SUSPENDED", "suspended creator",
            "CREATOR_UNSUSPENDED", "unsuspended creator",
            "CREATOR_APPLICATION_APPROVED", "approved application for creator",
            "CREATOR_APPLICATION_REJECTED", "rejected application for creator",
            "CREATOR_VERIFICATION_APPROVED", "approved verification for creator",
            "CREATOR_VERIFICATION_REJECTED", "rejected verification for creator"
    );

    private final AuditLogRepository auditLogRepository;

    @GetMapping("/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecent(
            @RequestParam(defaultValue = "CREATOR") String type,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<String> actions = "CREATOR".equalsIgnoreCase(type) ? CREATOR_ACTIONS : CREATOR_ACTIONS;
        List<AuditLog> logs = auditLogRepository.findByTargetTypeAndActionInOrderByCreatedAtDesc(
                "CREATOR", actions, PageRequest.of(0, limit)
        );

        List<Map<String, Object>> result = logs.stream().map(log -> {
            String creatorId = extractCreatorId(log.getMetadata());
            String label = ACTION_LABELS.getOrDefault(log.getAction(), log.getAction().toLowerCase().replace('_', ' '));
            String message = "Admin " + label + " #" + creatorId;

            return Map.<String, Object>of(
                    "action", log.getAction(),
                    "creatorId", creatorId,
                    "message", message,
                    "timestamp", log.getCreatedAt().toString()
            );
        }).toList();

        return ResponseEntity.ok(result);
    }

    private String extractCreatorId(String metadata) {
        if (metadata == null) return "?";
        // metadata format: "creatorId:123"
        if (metadata.startsWith("creatorId:")) {
            return metadata.substring("creatorId:".length());
        }
        return metadata;
    }
}
