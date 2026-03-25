package com.joinlivora.backend.fraud.model;

import com.joinlivora.backend.user.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_explanation_logs", indexes = {
    @Index(name = "idx_risk_explanation_log_requester", columnList = "requester_id"),
    @Index(name = "idx_risk_explanation_log_explanation", columnList = "explanation_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskExplanationLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Role role;

    @Column(name = "explanation_id", nullable = false, updatable = false)
    private UUID explanationId;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
