package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_earnings_invoices", indexes = {
    @Index(name = "idx_creator_earnings_inv_creator", columnList = "creator_id"),
    @Index(name = "idx_creator_earnings_inv_status", columnList = "status"),
    @Index(name = "idx_creator_earnings_inv_num", columnList = "invoice_number", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorEarningsInvoice {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false, updatable = false)
    private User creator;

    @Column(name = "invoice_number", nullable = false, unique = true, updatable = false)
    private String invoiceNumber;

    @Column(name = "period_start", nullable = false, updatable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private Instant periodEnd;

    @Column(name = "gross_earnings", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal grossEarnings;

    @Column(name = "platform_fee", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal platformFee;

    @Column(name = "net_earnings", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal netEarnings;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(name = "creator_name", updatable = false)
    private String creatorName;

    @Column(name = "creator_address", updatable = false)
    private String creatorAddress;

    @Column(name = "creator_email", updatable = false)
    private String creatorEmail;

    @Column(name = "seller_name", updatable = false)
    private String sellerName;

    @Column(name = "seller_address", updatable = false)
    private String sellerAddress;

    @Column(name = "seller_email", updatable = false)
    private String sellerEmail;

    @Column(name = "seller_vat_number", updatable = false)
    private String sellerVatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreatorEarningsInvoiceStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
