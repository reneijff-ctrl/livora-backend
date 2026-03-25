package com.joinlivora.backend.fraud.controller;

import com.joinlivora.backend.fraud.dto.IpSecurityResponse;
import com.joinlivora.backend.fraud.dto.UserSecurityResponse;
import com.joinlivora.backend.fraud.repository.DeviceFingerprintRepository;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.service.IpReputationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/security")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSecurityController {

    private final DeviceFingerprintRepository deviceFingerprintRepository;
    private final IpReputationService ipReputationService;
    private final RuleFraudSignalRepository fraudSignalRepository;

    @GetMapping("/devices/{userId}")
    public ResponseEntity<UserSecurityResponse> getUserDevices(@PathVariable UUID userId) {
        Long id = userId.getLeastSignificantBits();
        UserSecurityResponse response = UserSecurityResponse.builder()
                .fingerprints(deviceFingerprintRepository.findAllByUserId(id))
                .signals(fraudSignalRepository.findTop10ByUserIdOrderByCreatedAtDesc(id))
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ip/{ip}")
    public ResponseEntity<IpSecurityResponse> getIpSecurity(@PathVariable String ip) {
        IpSecurityResponse response = IpSecurityResponse.builder()
                .reputation(ipReputationService.getReputation(ip))
                .signals(fraudSignalRepository.findAllByReasonContaining(ip))
                .build();
        return ResponseEntity.ok(response);
    }
}
