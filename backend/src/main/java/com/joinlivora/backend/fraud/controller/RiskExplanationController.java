package com.joinlivora.backend.fraud.controller;

import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.fraud.dto.SanitizedRiskExplanationDto;
import com.joinlivora.backend.fraud.model.RiskExplanation;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.fraud.service.RiskExplanationAuditService;
import com.joinlivora.backend.fraud.service.RiskExplanationService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskExplanationController {

    private final RiskExplanationService riskExplanationService;
    private final RiskExplanationAuditService riskExplanationAuditService;
    private final UserService userService;

    @GetMapping("/why")
    public ResponseEntity<SanitizedRiskExplanationDto> getMyLatestExplanation(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        UUID userUuid = new UUID(0L, user.getId());

        RiskExplanation explanation = riskExplanationService.getLatestExplanationForSubject(userUuid, RiskSubjectType.USER)
                .orElseThrow(() -> new ResourceNotFoundException("No risk explanation found for your account."));

        riskExplanationAuditService.logRequest(userUuid, user.getRole(), explanation.getId());

        return ResponseEntity.ok(mapToSanitizedDto(explanation));
    }

    private SanitizedRiskExplanationDto mapToSanitizedDto(RiskExplanation explanation) {
        String redactedText = redactInternalDetails(explanation.getExplanationText());
        return SanitizedRiskExplanationDto.builder()
                .id(explanation.getId())
                .decision(explanation.getDecision())
                .explanationText(redactedText)
                .generatedAt(explanation.getGeneratedAt())
                .build();
    }

    private String redactInternalDetails(String text) {
        if (text == null) return null;

        // Rule 1: Remove exact thresholds (e.g., "Threshold 0.8 exceeded")
        String redacted = text.replaceAll("(?i)threshold\\s+\\d+(\\.\\d+)?", "System-detected risk patterns");

        // Rule 2: Remove internal model names (e.g., "Model v2.1")
        redacted = redacted.replaceAll("(?i)model\\s+[v\\d.]+", "System-detected risk patterns");

        // Rule 3: Remove AI confidence scores (e.g., "confidence 0.85")
        redacted = redacted.replaceAll("(?i)confidence\\s+\\d+(\\.\\d+)?", "System-detected risk patterns");

        // Rule 4: Remove internal system sources typically found in [brackets] (e.g., "[AML Engine]")
        redacted = redacted.replaceAll("\\[[^\\]]*(Engine|System|Detection|Tracker|Tracker|Evaluation|Reputation)[^\\]]*\\]", "[System-detected risk patterns]");

        // Clean up: Replace multiple consecutive "System-detected risk patterns" with a single one
        // Also handle the potential duplication when we replace both bracketed source and internal detail
        redacted = redacted.replaceAll("(System-detected risk patterns(\\s*[,.]\\s*)?)+", "System-detected risk patterns");
        redacted = redacted.replaceAll("\\[System-detected risk patterns\\]", "System-detected risk patterns");

        return redacted.trim();
    }
}
