package com.joinlivora.backend.reputation.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "reputation_events", indexes = {
    @Index(name = "idx_reputation_event_creator", columnList = "creator_id"),
    @Index(name = "idx_reputation_event_type", columnList = "type"),
    @Index(name = "idx_reputation_event_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReputationEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private UUID creatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReputationEventType type;

    @Min(-100)
    @Max(100)
    @Column(name = "delta_score", nullable = false, updatable = false)
    private int deltaScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReputationEventSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
