package com.joinlivora.backend.fraud.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_decisions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "related_tip_id")
    private Long relatedTipId;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 50)
    private FraudRiskLevel riskLevel;

    @Column(name = "reasons")
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
