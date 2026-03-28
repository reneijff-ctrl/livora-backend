package com.joinlivora.backend.wallet;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_wallets", indexes = {
    @Index(name = "idx_user_wallet_user", columnList = "user_id", unique = true)
})
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User userId;

    @Builder.Default
    @Column(nullable = false)
    private long balance = 0;

    @Builder.Default
    @Column(name = "reserved_balance", nullable = false)
    private long reservedBalance = 0;

    @Column(nullable = false)
    private Instant updatedAt;

    public long getAvailableBalance() {
        return balance - reservedBalance;
    }

    @PrePersist
    protected void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
