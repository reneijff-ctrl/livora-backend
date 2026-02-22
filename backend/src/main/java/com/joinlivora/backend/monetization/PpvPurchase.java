package com.joinlivora.backend.monetization;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ppv_purchases")
@Getter
@Setter
@ToString(exclude = {"creator", "ppvContent"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PpvPurchase {

    @Id
    @GeneratedValue
    @UuidGenerator
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ppv_content_id", nullable = false)
    private PpvContent ppvContent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private BigDecimal amount;

    private String stripePaymentIntentId;

    private String clientRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PpvPurchaseStatus status;

    @Column(nullable = false, updatable = false)
    private Instant purchasedAt;

    @PrePersist
    protected void onCreate() {
        purchasedAt = Instant.now();
    }
}
