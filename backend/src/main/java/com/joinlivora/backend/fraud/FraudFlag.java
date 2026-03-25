package com.joinlivora.backend.fraud;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_flags", indexes = {
    @Index(name = "idx_fraud_flag_user_id", columnList = "user_id"),
    @Index(name = "idx_fraud_flag_score", columnList = "score"),
    @Index(name = "idx_fraud_flag_stripe_charge", columnList = "stripe_charge_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudFlag {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "stripe_charge_id")
    private String stripeChargeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private FraudFlagSource source;

    @Column(name = "score")
    private Integer score;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
