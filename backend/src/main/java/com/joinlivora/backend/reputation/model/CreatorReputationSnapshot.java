package com.joinlivora.backend.reputation.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_reputation_snapshot", indexes = {
    @Index(name = "idx_creator_reputation_snapshot_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorReputationSnapshot {

    @Id
    @Column(name = "creator_id", nullable = false, updatable = false)
    private UUID creatorId;

    @Min(0)
    @Max(100)
    @Column(name = "current_score", nullable = false)
    private int currentScore;

    @Column(name = "last_decay_at")
    private Instant lastDecayAt;

    @Column(name = "last_positive_event_at")
    private Instant lastPositiveEventAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReputationStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
