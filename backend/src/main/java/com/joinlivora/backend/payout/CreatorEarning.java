package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_earnings_history", indexes = {
    @Index(name = "idx_creator_earning_creator", columnList = "creator_id"),
    @Index(name = "idx_creator_earning_source_type", columnList = "source_type")
})
@Getter
@Setter
@ToString(exclude = "creator")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false)
    private BigDecimal grossAmount;

    @Column(nullable = false)
    private BigDecimal platformFee;

    @Column(nullable = false)
    private BigDecimal netAmount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EarningSource sourceType;

    private String stripeChargeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String stripeSessionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private CreatorEarningsInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_policy_id")
    private PayoutHoldPolicy holdPolicy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_hold_id")
    private PayoutHold payoutHold;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id")
    private CreatorPayout payout;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_request_id")
    private PayoutRequest payoutRequest;

    @Builder.Default
    @Column(nullable = false)
    private boolean locked = false;

    @Builder.Default
    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
