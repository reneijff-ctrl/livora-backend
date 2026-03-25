package com.joinlivora.backend.aml.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "AmlRiskScore")
@Table(name = "aml_risk_scores", indexes = {
    @Index(name = "idx_aml_risk_score_user_id", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskScore {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private String level; // LOW | MEDIUM | HIGH | CRITICAL

    @Column(name = "last_evaluated_at", nullable = false)
    private Instant lastEvaluatedAt;
}
