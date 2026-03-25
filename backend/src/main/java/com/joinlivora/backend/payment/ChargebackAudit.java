package com.joinlivora.backend.payment;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chargeback_audits", indexes = {
    @Index(name = "idx_chargeback_audit_chargeback", columnList = "chargeback_id"),
    @Index(name = "idx_chargeback_audit_user", columnList = "user_id"),
    @Index(name = "idx_chargeback_audit_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargebackAudit {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "chargeback_id", nullable = false)
    private UUID chargebackId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "cluster_size")
    private Integer clusterSize;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "actions_taken", columnDefinition = "TEXT")
    private String actionsTaken;

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
