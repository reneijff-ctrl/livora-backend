package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.dto.IpReputation;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.DeviceFingerprint;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.repository.DeviceFingerprintRepository;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustEvaluationServiceTest {

    @Mock
    private DeviceFingerprintRepository deviceFingerprintRepository;

    @Mock
    private IpReputationService ipReputationService;

    @Mock
    private RuleFraudSignalRepository fraudSignalRepository;

    @Mock
    private FraudDetectionService fraudDetectionService;

    @Mock
    private RiskDecisionEngine riskDecisionEngine;

    @InjectMocks
    private TrustEvaluationService trustEvaluationService;

    private User user;
    private final String fingerprintHash = "test-fingerprint";
    private final String ipAddress = "1.2.3.4";

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
    }

    @Test
    void evaluate_TrustedDevice_ShouldAllow() {
        DeviceFingerprint device = DeviceFingerprint.builder()
                .userId(1L)
                .fingerprintHash(fingerprintHash)
                .ipAddress(ipAddress)
                .trusted(true)
                .build();

        when(deviceFingerprintRepository.findByUserIdAndFingerprintHash(1L, fingerprintHash))
                .thenReturn(Optional.of(device));
        when(ipReputationService.getReputation(ipAddress))
                .thenReturn(IpReputation.builder().proxy(false).vpn(false).tor(false).build());
        when(deviceFingerprintRepository.findAllByFingerprintHash(fingerprintHash))
                .thenReturn(List.of(device));
        
        when(riskDecisionEngine.evaluate(any(), any(), eq(10), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());

        RiskDecisionResult result = trustEvaluationService.evaluate(user, fingerprintHash, ipAddress);

        assertEquals(RiskDecision.ALLOW, result.getDecision());
    }

    @Test
    void evaluate_NewDeviceNewIp_ShouldChallengeAndLogSignals() {
        when(deviceFingerprintRepository.findByUserIdAndFingerprintHash(1L, fingerprintHash))
                .thenReturn(Optional.empty());
        when(deviceFingerprintRepository.findAllByUserId(1L))
                .thenReturn(Collections.emptyList());
        when(ipReputationService.getReputation(ipAddress))
                .thenReturn(IpReputation.builder().proxy(false).vpn(false).tor(false).build());
        when(deviceFingerprintRepository.findAllByFingerprintHash(fingerprintHash))
                .thenReturn(Collections.emptyList());
        
        when(riskDecisionEngine.evaluate(any(), any(), eq(60), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.REVIEW).build());

        RiskDecisionResult result = trustEvaluationService.evaluate(user, fingerprintHash, ipAddress);

        assertEquals(RiskDecision.REVIEW, result.getDecision()); // Score = 2 -> 60 -> REVIEW
        verify(fraudDetectionService).logFraudSignal(eq(1L), any(), any(), eq(com.joinlivora.backend.fraud.model.FraudSignalType.NEW_DEVICE), anyString());
        verify(fraudDetectionService).logFraudSignal(eq(1L), any(), any(), eq(com.joinlivora.backend.fraud.model.FraudSignalType.NEW_IP), anyString());
    }

    @Test
    void evaluate_Vpn_ShouldChallengeAndLogSignal() {
        DeviceFingerprint device = DeviceFingerprint.builder()
                .userId(1L)
                .fingerprintHash(fingerprintHash)
                .ipAddress(ipAddress)
                .trusted(false)
                .build();

        when(deviceFingerprintRepository.findByUserIdAndFingerprintHash(1L, fingerprintHash))
                .thenReturn(Optional.of(device));
        when(ipReputationService.getReputation(ipAddress))
                .thenReturn(IpReputation.builder().vpn(true).build());
        when(deviceFingerprintRepository.findAllByFingerprintHash(fingerprintHash))
                .thenReturn(List.of(device));
        
        when(riskDecisionEngine.evaluate(any(), any(), eq(60), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.REVIEW).build());

        RiskDecisionResult result = trustEvaluationService.evaluate(user, fingerprintHash, ipAddress);

        assertEquals(RiskDecision.REVIEW, result.getDecision()); // Score = 2 -> 60 -> REVIEW
        verify(fraudDetectionService).logFraudSignal(eq(1L), any(), any(), eq(com.joinlivora.backend.fraud.model.FraudSignalType.VPN_PROXY), anyString());
    }

    @Test
    void evaluate_Tor_ShouldChallengeAndLogSignal() {
        DeviceFingerprint device = DeviceFingerprint.builder()
                .userId(1L)
                .fingerprintHash(fingerprintHash)
                .ipAddress(ipAddress)
                .trusted(false)
                .build();

        when(deviceFingerprintRepository.findByUserIdAndFingerprintHash(1L, fingerprintHash))
                .thenReturn(Optional.of(device));
        when(ipReputationService.getReputation(ipAddress))
                .thenReturn(IpReputation.builder().tor(true).build());
        when(deviceFingerprintRepository.findAllByFingerprintHash(fingerprintHash))
                .thenReturn(List.of(device));
        
        when(riskDecisionEngine.evaluate(any(), any(), eq(60), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.REVIEW).build());

        RiskDecisionResult result = trustEvaluationService.evaluate(user, fingerprintHash, ipAddress);

        assertEquals(RiskDecision.REVIEW, result.getDecision()); // Score = 2 -> 60 -> REVIEW
        verify(fraudDetectionService).logFraudSignal(eq(1L), any(), any(), eq(com.joinlivora.backend.fraud.model.FraudSignalType.TOR_EXIT), anyString());
    }

    @Test
    void evaluate_DeviceMismatch_ShouldLogSignal() {
        DeviceFingerprint device = DeviceFingerprint.builder()
                .userId(1L)
                .fingerprintHash(fingerprintHash)
                .ipAddress(ipAddress)
                .trusted(false)
                .build();
        
        DeviceFingerprint otherUserDevice = DeviceFingerprint.builder()
                .userId(2L)
                .fingerprintHash(fingerprintHash)
                .build();

        when(deviceFingerprintRepository.findByUserIdAndFingerprintHash(1L, fingerprintHash))
                .thenReturn(Optional.of(device));
        when(ipReputationService.getReputation(ipAddress))
                .thenReturn(IpReputation.builder().build());
        when(deviceFingerprintRepository.findAllByFingerprintHash(fingerprintHash))
                .thenReturn(List.of(device, otherUserDevice));
        
        when(riskDecisionEngine.evaluate(any(), any(), anyInt(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());

        trustEvaluationService.evaluate(user, fingerprintHash, ipAddress);

        verify(fraudDetectionService).logFraudSignal(eq(1L), any(), any(), eq(com.joinlivora.backend.fraud.model.FraudSignalType.DEVICE_MISMATCH), anyString());
    }

    @Test
    void evaluate_FlaggedFingerprint_ShouldBlock() {
        DeviceFingerprint device = DeviceFingerprint.builder()
                .userId(1L)
                .fingerprintHash(fingerprintHash)
                .ipAddress(ipAddress)
                .trusted(false)
                .build();

        when(deviceFingerprintRepository.findByUserIdAndFingerprintHash(1L, fingerprintHash))
                .thenReturn(Optional.of(device));
        when(ipReputationService.getReputation(ipAddress))
                .thenReturn(IpReputation.builder().proxy(false).vpn(false).tor(false).build());
        
        when(deviceFingerprintRepository.findAllByFingerprintHash(fingerprintHash))
                .thenReturn(List.of(device));
        when(fraudSignalRepository.existsByUserIdAndRiskLevelAndResolvedFalse(1L, FraudDecisionLevel.HIGH))
                .thenReturn(true);
        
        when(riskDecisionEngine.evaluate(any(), any(), eq(100), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.BLOCK).build());

        RiskDecisionResult result = trustEvaluationService.evaluate(user, fingerprintHash, ipAddress);

        assertEquals(RiskDecision.BLOCK, result.getDecision()); // Score = 3 -> 100 -> BLOCK
    }

    @Test
    void evaluate_MultipleRiskFactors_ShouldBlock() {
        // New Device + New IP (2) + VPN (2) = 4
        when(deviceFingerprintRepository.findByUserIdAndFingerprintHash(1L, fingerprintHash))
                .thenReturn(Optional.empty());
        when(deviceFingerprintRepository.findAllByUserId(1L))
                .thenReturn(Collections.emptyList());
        when(ipReputationService.getReputation(ipAddress))
                .thenReturn(IpReputation.builder().vpn(true).build());
        when(deviceFingerprintRepository.findAllByFingerprintHash(fingerprintHash))
                .thenReturn(Collections.emptyList());
        
        when(riskDecisionEngine.evaluate(any(), any(), eq(100), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.BLOCK).build());

        RiskDecisionResult result = trustEvaluationService.evaluate(user, fingerprintHash, ipAddress);

        assertEquals(RiskDecision.BLOCK, result.getDecision());
    }
}








