package com.joinlivora.backend.payout;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "legacy_creator_stripe_accounts", indexes = {
    @Index(name = "idx_legacy_creator_stripe_account_creator", columnList = "creator_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyCreatorStripeAccount {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false, unique = true)
    private Long creatorId;

    @Column(name = "stripe_account_id", nullable = false)
    private String stripeAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false)
    @Builder.Default
    private com.joinlivora.backend.creator.model.StripeOnboardingStatus onboardingStatus = com.joinlivora.backend.creator.model.StripeOnboardingStatus.NOT_STARTED;

    @Builder.Default
    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    @Builder.Default
    @Column(name = "payouts_enabled", nullable = false)
    private boolean payoutsEnabled = false;

    @Builder.Default
    @Column(name = "charges_enabled", nullable = false)
    private boolean chargesEnabled = false;

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
