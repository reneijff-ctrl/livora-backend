package com.joinlivora.backend.fraud.controller;

import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.fraud.model.RiskExplanation;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.fraud.service.RiskExplanationAuditService;
import com.joinlivora.backend.fraud.service.RiskExplanationService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/risk")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRiskExplanationController {

    private final RiskExplanationService riskExplanationService;
    private final RiskExplanationAuditService riskExplanationAuditService;
    private final UserService userService;

    @GetMapping("/explanations/{subjectType}/{subjectId}")
    public ResponseEntity<List<RiskExplanation>> getExplanationsForSubject(
            @PathVariable RiskSubjectType subjectType,
            @PathVariable UUID subjectId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User admin = userService.getByEmail(userDetails.getUsername());
        UUID adminUuid = new UUID(0L, admin.getId());
        List<RiskExplanation> explanations = riskExplanationService.getExplanationsForSubject(subjectId, subjectType);
        
        explanations.forEach(e -> riskExplanationAuditService.logRequest(adminUuid, admin.getRole(), e.getId()));
        
        return ResponseEntity.ok(explanations);
    }

    @GetMapping("/explanation/{id}")
    public ResponseEntity<RiskExplanation> getExplanationById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User admin = userService.getByEmail(userDetails.getUsername());
        UUID adminUuid = new UUID(0L, admin.getId());
        
        return riskExplanationService.getExplanationById(id)
                .map(explanation -> {
                    riskExplanationAuditService.logRequest(adminUuid, admin.getRole(), explanation.getId());
                    return ResponseEntity.ok(explanation);
                })
                .orElseThrow(() -> new ResourceNotFoundException("Risk explanation not found: " + id));
    }
}
