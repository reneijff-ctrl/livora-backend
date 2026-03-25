package com.joinlivora.backend.payout;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_payout_settings", indexes = {
    @Index(name = "idx_creator_payout_settings_creator", columnList = "creator_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorPayoutSettings {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method", nullable = false)
    private PayoutMethod payoutMethod;

    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    @Column(name = "minimum_payout_amount", precision = 19, scale = 2)
    private BigDecimal minimumPayoutAmount;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
