package com.joinlivora.backend.payment;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices", indexes = {
    @Index(name = "idx_invoice_user", columnList = "user_id"),
    @Index(name = "idx_invoice_number", columnList = "invoice_number", unique = true),
    @Index(name = "idx_invoice_stripe_id", columnList = "stripe_invoice_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User userId;

    @Column(name = "invoice_number", nullable = false, unique = true, updatable = false)
    private String invoiceNumber;

    @Column(name = "gross_amount", nullable = false, updatable = false)
    private BigDecimal grossAmount;

    @Column(name = "vat_amount", nullable = false, updatable = false)
    private BigDecimal vatAmount;

    @Column(name = "net_amount", nullable = false, updatable = false)
    private BigDecimal netAmount;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(name = "country_code", nullable = false, updatable = false)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, updatable = false)
    private InvoiceType invoiceType;

    @Column(name = "stripe_invoice_id", updatable = false)
    private String stripeInvoiceId;

    @Column(name = "billing_name", updatable = false)
    private String billingName;

    @Column(name = "billing_address", updatable = false)
    private String billingAddress;

    @Column(name = "billing_email", updatable = false)
    private String billingEmail;

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
    private InvoiceStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @PrePersist
    protected void onCreate() {
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
    }
}
