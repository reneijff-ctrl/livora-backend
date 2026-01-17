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
    @Index(name = "idx_creator_earning_source", columnList = "source")
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
    private BigDecimal amountGross;

    @Column(nullable = false)
    private BigDecimal platformFee;

    @Column(nullable = false)
    private BigDecimal amountNet;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EarningSource source;

    private String stripeChargeId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
