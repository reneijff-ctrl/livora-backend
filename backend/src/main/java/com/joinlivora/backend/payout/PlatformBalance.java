package com.joinlivora.backend.payout;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity to track the platform's aggregate earnings from transaction fees.
 * This represents the "platform balance" mentioned in the requirements.
 */
@Entity
@Table(name = "platform_balances")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBalance {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Builder.Default
    @Column(name = "total_fees_collected", nullable = false)
    private BigDecimal totalFeesCollected = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_creator_earnings", nullable = false)
    private BigDecimal totalCreatorEarnings = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "available_balance", nullable = false)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
