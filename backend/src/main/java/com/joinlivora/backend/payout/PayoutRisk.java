package com.joinlivora.backend.payout;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_risks", indexes = {
    @Index(name = "idx_payout_risk_user", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRisk {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "reasons", columnDefinition = "TEXT")
    private String reasons;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @PrePersist
    protected void onCreate() {
        if (lastEvaluatedAt == null) {
            lastEvaluatedAt = Instant.now();
        }
    }
}
