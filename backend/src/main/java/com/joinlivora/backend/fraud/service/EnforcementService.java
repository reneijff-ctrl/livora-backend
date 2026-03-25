package com.joinlivora.backend.fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.fraud.model.EnforcementAction;
import com.joinlivora.backend.fraud.model.FraudEvent;
import com.joinlivora.backend.fraud.model.FraudEventType;
import com.joinlivora.backend.fraud.model.FraudScore;
import com.joinlivora.backend.fraud.repository.FraudEventRepository;
import com.joinlivora.backend.payment.AutoFreezePolicyService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service("enforcementService")
@RequiredArgsConstructor
@Slf4j
public class EnforcementService {

    private final AutoFreezePolicyService autoFreezePolicyService;
    private final UserRepository userRepository;
    private final FraudEventRepository fraudEventRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Transactional
    public void freezePayouts(UUID userId, String reason) {
        freezePayouts(userId, reason, null, null, null, "SYSTEM", "INTERNAL", null, null, null);
    }

    @Transactional
    public void freezePayouts(UUID userId, String reason, Integer count, Double rate, String stripeEventId, String triggeredBy, String source, String ipAddress, Integer riskScore, Integer thresholdReached) {
        userRepository.findById(userId.getLeastSignificantBits()).ifPresent(user -> {
            if (user.getStatus() != UserStatus.PAYOUTS_FROZEN && 
                user.getStatus() != UserStatus.SUSPENDED &&
                user.getStatus() != UserStatus.MANUAL_REVIEW) {
                
                log.info("ENFORCEMENT: Freezing payouts for creator {} (ID: {}). Reason: {}", user.getEmail(), userId, reason);
                autoFreezePolicyService.freezePayouts(user, reason);
                persistFraudEvent(userId, FraudEventType.PAYOUT_FROZEN, reason, count, rate, stripeEventId, triggeredBy, source, ipAddress, riskScore, thresholdReached);
                
                logAudit(userId, AuditService.PAYOUT_FROZEN, triggeredBy, reason, ipAddress);
            } else {
                log.info("ENFORCEMENT: Payout freeze skipped for creator {} (Status: {}). Already restricted.", userId, user.getStatus());
            }
        });
    }

    @Transactional
    public void suspendAccount(UUID userId, String reason) {
        suspendAccount(userId, reason, null, null, null, "SYSTEM", "INTERNAL", null, null, null);
    }

    @Transactional
    public void suspendAccount(UUID userId, String reason, Integer count, Double rate, String stripeEventId, String triggeredBy, String source, String ipAddress, Integer riskScore, Integer thresholdReached) {
        userRepository.findById(userId.getLeastSignificantBits()).ifPresent(user -> {
            if (user.getStatus() != UserStatus.SUSPENDED) {
                log.warn("ENFORCEMENT: Suspending account for creator {} (ID: {}). Reason: {}", user.getEmail(), userId, reason);
                autoFreezePolicyService.suspendAccount(user, reason);
                persistFraudEvent(userId, FraudEventType.ACCOUNT_SUSPENDED, reason, count, rate, stripeEventId, triggeredBy, source, ipAddress, riskScore, thresholdReached);
                
                logAudit(userId, AuditService.ACCOUNT_SUSPENDED, triggeredBy, reason, ipAddress);
            } else {
                log.info("ENFORCEMENT: Account suspension skipped for creator {}. Already suspended.", userId);
            }
        });
    }

    @Transactional
    public void terminateAccount(UUID userId, String reason) {
        terminateAccount(userId, reason, null, null, null, "SYSTEM", "INTERNAL", null, null, null);
    }

    @Transactional
    public void terminateAccount(UUID userId, String reason, Integer count, Double rate, String stripeEventId, String triggeredBy, String source, String ipAddress, Integer riskScore, Integer thresholdReached) {
        userRepository.findById(userId.getLeastSignificantBits()).ifPresent(user -> {
            // Even if suspended, we might want to flag as terminated in logs/fraud score
            log.error("ENFORCEMENT: Terminating account for creator {} (ID: {}). Reason: {}", user.getEmail(), userId, reason);
            
            if (user.getStatus() != UserStatus.TERMINATED) {
                autoFreezePolicyService.terminateAccount(user, reason);
                persistFraudEvent(userId, FraudEventType.ACCOUNT_TERMINATED, reason, count, rate, stripeEventId, triggeredBy, source, ipAddress, riskScore, thresholdReached);
                
                logAudit(userId, AuditService.ACCOUNT_TERMINATED, triggeredBy, reason, ipAddress);
            } else {
                log.info("ENFORCEMENT: Account termination skipped for creator {}. Already terminated.", userId);
            }
        });
    }

    private void logAudit(UUID targetUserId, String action, String triggeredBy, String reason, String ipAddress) {
        UUID actorId = null;
        if (triggeredBy != null && triggeredBy.contains("@")) {
            actorId = userRepository.findByEmail(triggeredBy)
                    .map(u -> new UUID(0L, u.getId()))
                    .orElse(null);
        }

        auditService.logEvent(
                actorId,
                action,
                "USER",
                targetUserId,
                Map.of("type", reason != null ? reason : "", "triggeredBy", triggeredBy != null ? triggeredBy : "SYSTEM"),
                ipAddress,
                null
        );
    }

    @Transactional
    public void recordChargeback(UUID userId, String reason, Integer count, Double rate, String stripeEventId, String triggeredBy, String source, String ipAddress) {
        persistFraudEvent(userId, FraudEventType.CHARGEBACK_REPORTED, reason, count, rate, stripeEventId, triggeredBy, source, ipAddress, null, null);
    }

    @Transactional
    public void recordManualOverride(UUID userId, String reason, String triggeredBy, String source, String ipAddress) {
        persistFraudEvent(userId, FraudEventType.MANUAL_OVERRIDE, reason, null, null, null, triggeredBy, source, ipAddress, null, null);
    }

    @Transactional
    public void recordPaymentSuccess(UUID userId, String paymentIntentId, Long amount, String currency, String stripeEventId, String ipAddress) {
        Map<String, Object> extraMetadata = new java.util.HashMap<>();
        extraMetadata.put("paymentIntentId", paymentIntentId);
        extraMetadata.put("amount", amount);
        extraMetadata.put("currency", currency);
        persistFraudEvent(userId, FraudEventType.PAYMENT_SUCCESS, "Successful payment: " + paymentIntentId, null, null, stripeEventId, "SYSTEM", "STRIPE", ipAddress, null, null, extraMetadata);
    }

    @Transactional
    public void recordFraudIncident(UUID userId, String reason, Map<String, Object> metadata) {
        log.warn("ENFORCEMENT: Recording fraud incident for creator {}: {}", userId, reason);
        persistFraudEvent(userId, FraudEventType.CRITICAL_RISK_DETECTED, reason, null, null, null, "SYSTEM", "INTERNAL", null, null, null, metadata);
    }

    private void persistFraudEvent(UUID userId, FraudEventType type, String reason, Integer count, Double rate, String stripeEventId, String triggeredBy, String source, String ipAddress, Integer riskScore, Integer thresholdReached) {
        persistFraudEvent(userId, type, reason, count, rate, stripeEventId, triggeredBy, source, ipAddress, riskScore, thresholdReached, null);
    }

    private void persistFraudEvent(UUID userId, FraudEventType type, String reason, Integer count, Double rate, String stripeEventId, String triggeredBy, String source, String ipAddress, Integer riskScore, Integer thresholdReached, Map<String, Object> extraMetadata) {
        try {
            Map<String, Object> metadataMap = new java.util.HashMap<>();
            if (count != null) metadataMap.put("chargebackCount", count);
            if (rate != null) metadataMap.put("rate", rate);
            if (stripeEventId != null) metadataMap.put("stripeEventId", stripeEventId);
            if (triggeredBy != null) metadataMap.put("triggeredBy", triggeredBy);
            if (source != null) metadataMap.put("source", source);
            if (ipAddress != null) metadataMap.put("ipAddress", ipAddress);
            if (riskScore != null) metadataMap.put("riskScore", riskScore);
            if (thresholdReached != null) metadataMap.put("thresholdReached", thresholdReached);
            if (extraMetadata != null) metadataMap.putAll(extraMetadata);

            String metadata = objectMapper.writeValueAsString(metadataMap);

            FraudEvent event = FraudEvent.builder()
                    .userId(userId)
                    .eventType(type)
                    .reason(reason)
                    .metadata(metadata)
                    .createdAt(Instant.now())
                    .build();

            fraudEventRepository.save(event);
            log.info("ENFORCEMENT: Persisted FraudEvent {} for creator {}", type, userId);
        } catch (JsonProcessingException e) {
            log.error("ENFORCEMENT: Failed to serialize metadata for FraudEvent", e);
        }
    }

}
