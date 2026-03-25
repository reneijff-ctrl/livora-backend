package com.joinlivora.backend.payouts.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creators", indexes = {
    @Index(name = "idx_creator_account_creator_id", columnList = "creator_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorAccount {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false, unique = true)
    private UUID creatorId;

    @Builder.Default
    @Column(name = "payout_frozen", nullable = false)
    private boolean payoutFrozen = false;

    @Column(name = "freeze_reason")
    private String freezeReason;

    @Column(name = "frozen_at")
    private Instant frozenAt;
}
