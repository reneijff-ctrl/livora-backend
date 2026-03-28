package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stripe_accounts", indexes = {
    @Index(name = "idx_stripe_account_user", columnList = "user_id", unique = true),
    @Index(name = "idx_stripe_account_id", columnList = "stripe_account_id", unique = true)
})
@Getter
@Setter
@ToString(exclude = "user")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "stripe_account_id", unique = true)
    private String stripeAccountId;

    @Builder.Default
    @Column(nullable = false)
    private boolean onboardingCompleted = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean chargesEnabled = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean payoutsEnabled = false;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
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
