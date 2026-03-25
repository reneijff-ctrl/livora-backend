package com.joinlivora.backend.payouts.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_freeze_history", indexes = {
    @Index(name = "idx_payout_freeze_history_creator_id", columnList = "creator_id"),
    @Index(name = "idx_payout_freeze_history_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutFreezeHistory {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(nullable = false)
    private String reason;

    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy; // SYSTEM | ADMIN

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
