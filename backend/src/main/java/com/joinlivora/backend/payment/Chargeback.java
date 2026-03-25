package com.joinlivora.backend.payment;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "legacy_chargebacks", indexes = {
    @Index(name = "idx_chargeback_transaction", columnList = "transaction_id"),
    @Index(name = "idx_chargeback_user", columnList = "user_id"),
    @Index(name = "idx_chargeback_creator", columnList = "creator_id"),
    @Index(name = "idx_chargeback_stripe_charge", columnList = "stripe_charge_id"),
    @Index(name = "idx_chargeback_stripe_dispute", columnList = "stripe_dispute_id"),
    @Index(name = "idx_chargeback_device_fingerprint", columnList = "device_fingerprint"),
    @Index(name = "idx_chargeback_ip_address", columnList = "ip_address"),
    @Index(name = "idx_chargeback_pm_fingerprint", columnList = "payment_method_fingerprint")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chargeback {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "stripe_charge_id", nullable = false)
    private String stripeChargeId;

    @Column(name = "stripe_dispute_id")
    private String stripeDisputeId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "currency")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChargebackStatus status;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "payment_method_fingerprint")
    private String paymentMethodFingerprint;

    @Column(name = "payment_method_brand")
    private String paymentMethodBrand;

    @Column(name = "payment_method_last4")
    private String paymentMethodLast4;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "resolved")
    private boolean resolved;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (status == null) {
            status = ChargebackStatus.RECEIVED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
