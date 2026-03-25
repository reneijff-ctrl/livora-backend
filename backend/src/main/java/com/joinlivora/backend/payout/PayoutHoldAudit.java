package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.model.RiskSubjectType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_hold_audits", indexes = {
    @Index(name = "idx_payout_hold_audit_subject", columnList = "subject_type, subject_id"),
    @Index(name = "idx_payout_hold_audit_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutHoldAudit {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private RiskSubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "action", nullable = false)
    private String action; // APPLIED, OVERRIDDEN, RELEASED

    @Enumerated(EnumType.STRING)
    @Column(name = "prev_hold_level")
    private HoldLevel prevHoldLevel;

    @Column(name = "prev_hold_days")
    private Integer prevHoldDays;

    @Column(name = "prev_expires_at")
    private Instant prevExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_hold_level", nullable = false)
    private HoldLevel newHoldLevel;

    @Column(name = "new_hold_days", nullable = false)
    private Integer newHoldDays;

    @Column(name = "new_expires_at")
    private Instant newExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PayoutActorType type;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
