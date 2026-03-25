package com.joinlivora.backend.fraud.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "fraud_risk_scores")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRiskScore {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private int score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> factors;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        evaluatedAt = Instant.now();
    }
}
