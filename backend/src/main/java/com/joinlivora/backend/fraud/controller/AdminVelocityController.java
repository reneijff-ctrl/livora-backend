package com.joinlivora.backend.fraud.controller;

import com.joinlivora.backend.fraud.dto.UserVelocityResponse;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.UserRiskState;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.fraud.repository.VelocityMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/velocity")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVelocityController {

    private final UserRiskStateRepository userRiskStateRepository;
    private final VelocityMetricRepository velocityMetricRepository;
    private final RuleFraudSignalRepository fraudSignalRepository;

    @GetMapping("/{userId}")
    public ResponseEntity<UserVelocityResponse> getUserVelocity(@PathVariable UUID userId) {
        Long id = userId.getLeastSignificantBits();
        
        UserRiskState riskState = userRiskStateRepository.findById(id)
                .orElse(UserRiskState.builder()
                        .userId(id)
                        .currentRisk(FraudDecisionLevel.LOW)
                        .paymentLocked(false)
                        .build());

        UserVelocityResponse response = UserVelocityResponse.builder()
                .userId(id)
                .riskLevel(riskState.getCurrentRisk())
                .paymentLocked(riskState.isPaymentLocked())
                .blockedUntil(riskState.getBlockedUntil())
                .currentMetrics(velocityMetricRepository.findAllByUserIdAndWindowEndAfter(id, Instant.now().minus(1, ChronoUnit.HOURS)))
                .recentSignals(fraudSignalRepository.findTop10ByUserIdOrderByCreatedAtDesc(id))
                .build();

        return ResponseEntity.ok(response);
    }
}
