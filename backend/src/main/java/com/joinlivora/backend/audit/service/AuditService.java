package com.joinlivora.backend.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.audit.model.AuditLog;
import com.joinlivora.backend.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    
    public static final String USER_LOGIN = "USER_LOGIN";
    public static final String USER_LOGIN_FAILURE = "USER_LOGIN_FAILURE";
    public static final String USER_LOGOUT = "USER_LOGOUT";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String TIP_CREATED = "TIP_CREATED";
    public static final String PAYOUT_REQUESTED = "PAYOUT_REQUESTED";
    public static final String PAYOUT_EXECUTED = "PAYOUT_EXECUTED";
    public static final String PAYOUT_BLOCKED = "PAYOUT_BLOCKED";
    public static final String AML_FLAGGED = "AML_FLAGGED";
    public static final String AML_PAYOUT_FROZEN = "AML_PAYOUT_FROZEN";
    public static final String PAYOUT_FROZEN = "PAYOUT_FROZEN";
    public static final String PAYOUT_UNFROZEN = "PAYOUT_UNFROZEN";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";
    public static final String ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED";
    public static final String ACCOUNT_UNSUSPENDED = "ACCOUNT_UNSUSPENDED";
    public static final String ACCOUNT_TERMINATED = "ACCOUNT_TERMINATED";
    public static final String USER_SHADOWBANNED = "USER_SHADOWBANNED";
    public static final String CONTENT_TAKEDOWN = "CONTENT_TAKEDOWN";
    public static final String ROOM_MODERATION = "ROOM_MODERATION";
    public static final String MANUAL_PAYOUT_EXECUTED = "MANUAL_PAYOUT_EXECUTED";
    public static final String PAYOUT_OVERRIDE = "PAYOUT_OVERRIDE";
    public static final String REFUND_CREATED = "REFUND_CREATED";
    public static final String CHARGEBACK_RECEIVED = "CHARGEBACK_RECEIVED";
    public static final String DRY_RUN_EARNING_RECORDED = "DRY_RUN_EARNING_RECORDED";
    public static final String TOTP_ENABLED = "TOTP_ENABLED";
    public static final String TOTP_DISABLED = "TOTP_DISABLED";
    public static final String TOTP_VERIFY_FAILED = "TOTP_VERIFY_FAILED";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Logs an audit event asynchronously.
     *
     * @param actorUserId the ID of the creator performing the action (nullable)
     * @param action      the action being performed (e.g. PAYOUT_FREEZE)
     * @param targetType  the type of the target resource (e.g. USER, PAYOUT)
     * @param targetId    the ID of the target resource (nullable)
     * @param metadata    additional data related to the event (Map, DTO or String)
     * @param ipAddress   the IP address of the actor
     * @param userAgent   the creator agent of the actor
     */
    @Async
    @Transactional
    public void logEvent(UUID actorUserId, String action, String targetType, UUID targetId, Object metadata, String ipAddress, String userAgent) {
        try {
            String normalizedMetadata = normalizeMetadata(metadata);

            AuditLog auditLog = AuditLog.builder()
                    .actorUserId(actorUserId)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .metadata(normalizedMetadata)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit log saved: {} - {} - {}", action, targetType, targetId);
        } catch (Exception e) {
            log.error("Failed to persist audit log for action: {}", action, e);
        }
    }

    /**
     * Retrieves the audit history for a specific actor.
     */
    @Transactional(readOnly = true)
    public java.util.List<AuditLog> getActorHistory(UUID actorUserId) {
        return auditLogRepository.findAllByActorUserIdOrderByCreatedAtDesc(actorUserId);
    }

    /**
     * Retrieves the audit history for a specific target resource.
     */
    @Transactional(readOnly = true)
    public java.util.List<AuditLog> getTargetHistory(String targetType, UUID targetId) {
        return auditLogRepository.findAllByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> searchLogs(String action, UUID userId, Instant fromDate, Instant toDate, Pageable pageable) {
        return auditLogRepository.findAll(createSpecification(action, userId, fromDate, toDate), pageable);
    }

    private Specification<AuditLog> createSpecification(String action, UUID userId, Instant fromDate, Instant toDate) {
        return (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();

            if (action != null && !action.isEmpty()) {
                predicates.add(cb.equal(root.get("action"), action));
            }

            if (userId != null) {
                predicates.add(cb.equal(root.get("actorUserId"), userId));
            }

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }

            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private String normalizeMetadata(Object metadata) {
        if (metadata == null) {
            return null;
        }
        if (metadata instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to normalize audit metadata to JSON string", e);
            return String.valueOf(metadata);
        }
    }
}
