package com.joinlivora.backend.fraud.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_scores")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskScore {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Min(0)
    @Max(100)
    @Column(nullable = false)
    private int score;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @Column(columnDefinition = "TEXT")
    private String breakdown;
}
