package com.joinlivora.backend.payout;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_audit_logs", indexes = {
    @Index(name = "idx_payout_audit_log_payout", columnList = "payout_id"),
    @Index(name = "idx_payout_audit_log_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutAuditLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payout_id", nullable = false)
    private UUID payoutId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private PayoutActorType actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private PayoutStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status")
    private PayoutStatus newStatus;

    @Column(length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
