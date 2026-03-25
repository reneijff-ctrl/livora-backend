package com.joinlivora.backend.fraud.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
@Entity
@Table(name = "user_risk_state", indexes = {
    @Index(name = "idx_user_risk_state_current_risk", columnList = "current_risk")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRiskState {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_risk", nullable = false)
    private FraudDecisionLevel currentRisk;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Builder.Default
    @Column(name = "payment_locked", nullable = false)
    private boolean paymentLocked = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
