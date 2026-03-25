package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutFrequency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_payout_states", indexes = {
    @Index(name = "idx_creator_payout_state_creator", columnList = "creator_id", unique = true),
    @Index(name = "idx_creator_payout_state_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorPayoutState {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "current_limit", precision = 19, scale = 2)
    private BigDecimal currentLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStateStatus status;

    @Builder.Default
    @Column(name = "manual_override", nullable = false)
    private boolean manualOverride = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
