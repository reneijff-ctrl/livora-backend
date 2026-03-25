package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutFrequency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_policy_decisions", indexes = {
    @Index(name = "idx_payout_policy_decision_creator", columnList = "creator_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutPolicyDecision {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "applied_limit_amount", precision = 19, scale = 2)
    private BigDecimal appliedLimitAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_limit_frequency", nullable = false)
    private PayoutFrequency appliedLimitFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_source", nullable = false)
    private DecisionSource decisionSource;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "explanation_id")
    private UUID explanationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
