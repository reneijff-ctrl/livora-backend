package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.dto.IpReputation;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.*;
import com.joinlivora.backend.fraud.repository.DeviceFingerprintRepository;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service("trustEvaluationService")
@Slf4j
@RequiredArgsConstructor
public class TrustEvaluationService {

    private final DeviceFingerprintRepository deviceFingerprintRepository;
    private final IpReputationService ipReputationService;
    private final RuleFraudSignalRepository fraudSignalRepository;
    private final FraudDetectionService fraudDetectionService;
    private final RiskDecisionEngine riskDecisionEngine;

    public RiskDecisionResult evaluate(User user, String fingerprintHash, String ipAddress) {
        log.info("Evaluating trust for creator: {}, IP: {}, Fingerprint: {}",
                user.getId(), maskIp(ipAddress), mask(fingerprintHash));

        int riskScore = 0;
        Map<String, Object> factors = new HashMap<>();

        // 1. Check if device is trusted
        Optional<DeviceFingerprint> deviceOpt = deviceFingerprintRepository.findByUserIdAndFingerprintHash(user.getId(), fingerprintHash);
        if (deviceOpt.isPresent() && deviceOpt.get().isTrusted()) {
            log.debug("Trusted device detected for creator {}, decreasing risk", user.getId());
            riskScore -= 2;
        }

        // 2. New device + new IP
        boolean isNewDevice = deviceOpt.isEmpty();
        boolean isNewIp = isNewIpForUser(user.getId(), ipAddress);

        if (isNewDevice) {
            log.debug("New device detected for creator: {}", user.getId());
            factors.put("NEW_DEVICE", true);
            fraudDetectionService.logFraudSignal(user.getId(), FraudDecisionLevel.LOW, FraudSource.SYSTEM, FraudSignalType.NEW_DEVICE, "New device detected: " + mask(fingerprintHash));
        }

        if (isNewIp) {
            log.debug("New IP detected for creator: {}", user.getId());
            factors.put("NEW_IP", true);
            fraudDetectionService.logFraudSignal(user.getId(), FraudDecisionLevel.LOW, FraudSource.SYSTEM, FraudSignalType.NEW_IP, "New IP detected: " + maskIp(ipAddress));
        }

        if (isNewDevice && isNewIp) {
            log.debug("New device + new IP combination detected for creator {}, increasing risk", user.getId());
            riskScore += 2;
        }

        // 3. VPN/Proxy/Tor
        IpReputation reputation = ipReputationService.getReputation(ipAddress);
        if (reputation.isProxy() || reputation.isVpn()) {
            log.debug("VPN/Proxy detected for creator {}, increasing risk", user.getId());
            factors.put("VPN_PROXY", true);
            fraudDetectionService.logFraudSignal(user.getId(), FraudDecisionLevel.MEDIUM, FraudSource.SYSTEM, FraudSignalType.VPN_PROXY, "VPN/Proxy detected: " + maskIp(ipAddress));
            riskScore += 2;
        }
        if (reputation.isTor()) {
            log.debug("Tor exit node detected for creator {}, increasing risk", user.getId());
            factors.put("TOR_EXIT", true);
            fraudDetectionService.logFraudSignal(user.getId(), FraudDecisionLevel.MEDIUM, FraudSource.SYSTEM, FraudSignalType.TOR_EXIT, "Tor exit node detected: " + maskIp(ipAddress));
            riskScore += 2;
        }

        // 4. Device Mismatch (Fingerprint used by multiple users)
        if (isFingerprintUsedByOthers(user.getId(), fingerprintHash)) {
            log.debug("Device mismatch detected for creator {}: fingerprint used by other users", user.getId());
            factors.put("DEVICE_MISMATCH", true);
            fraudDetectionService.logFraudSignal(user.getId(), FraudDecisionLevel.MEDIUM, FraudSource.SYSTEM, FraudSignalType.DEVICE_MISMATCH, "Device mismatch: fingerprint used by multiple users");
            riskScore += 1;
        }

        // 5. Previously flagged fingerprint
        if (isFingerprintFlagged(fingerprintHash)) {
            log.debug("Fingerprint previously flagged for creator {}, increasing risk", user.getId());
            factors.put("CHARGEBACK", true);
            riskScore += 3;
        }

        log.info("Final trust risk score for creator {}: {}", user.getId(), riskScore);

        // Normalize score for RiskDecisionEngine (0-100)
        int normalizedScore = riskScore >= 3 ? 100 : (riskScore >= 2 ? 60 : 10);

        return riskDecisionEngine.evaluate(RiskSubjectType.USER, new UUID(0L, user.getId()), normalizedScore, factors);
    }

    private String maskIp(String ip) {
        if (ip == null) return null;
        return ip.replaceAll("(\\d+)\\.(\\d+)\\..*", "$1.$2.***.***");
    }

    private String mask(String value) {
        if (value == null) return null;
        if (value.length() <= 8) return "********";
        return value.substring(0, 4) + "...." + value.substring(value.length() - 4);
    }

    private boolean isNewIpForUser(Long userId, String ipAddress) {
        List<DeviceFingerprint> userDevices = deviceFingerprintRepository.findAllByUserId(userId);
        return userDevices.stream().noneMatch(d -> ipAddress.equals(d.getIpAddress()));
    }

    private boolean isFingerprintUsedByOthers(Long userId, String fingerprintHash) {
        List<DeviceFingerprint> devicesWithSameHash = deviceFingerprintRepository.findAllByFingerprintHash(fingerprintHash);
        return devicesWithSameHash.stream()
                .anyMatch(d -> !userId.equals(d.getUserId()));
    }

    private boolean isFingerprintFlagged(String fingerprintHash) {
        List<DeviceFingerprint> devicesWithSameHash = deviceFingerprintRepository.findAllByFingerprintHash(fingerprintHash);
        return devicesWithSameHash.stream()
                .map(DeviceFingerprint::getUserId)
                .distinct()
                .anyMatch(userId -> fraudSignalRepository.existsByUserIdAndRiskLevelAndResolvedFalse(userId, FraudDecisionLevel.HIGH));
    }
}
