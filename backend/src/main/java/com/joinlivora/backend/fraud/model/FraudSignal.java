package com.joinlivora.backend.fraud.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a fraud scoring signal for persistent tracking.
 */
@Entity
@Table(name = "fraud_signals", indexes = {
    @Index(name = "idx_fraud_signal_user", columnList = "user_id"),
    @Index(name = "idx_fraud_signal_room", columnList = "room_id"),
    @Index(name = "idx_fraud_signal_created", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudSignal {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "score", nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private FraudRiskLevel riskLevel;

    @Column(name = "reasons", columnDefinition = "TEXT")
    private String reasons;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
