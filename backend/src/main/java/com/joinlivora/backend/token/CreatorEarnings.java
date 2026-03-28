package com.joinlivora.backend.token;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_earnings", indexes = {
    @Index(name = "idx_creator_earning_user", columnList = "user_id", unique = true)
})
@Getter
@Setter
@ToString(exclude = "user")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorEarnings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(nullable = false)
    private long totalEarnedTokens = 0;

    @Builder.Default
    @Column(nullable = false)
    private long availableTokens = 0;

    @Builder.Default
    @Column(nullable = false)
    private long lockedTokens = 0;

    @Column(nullable = false)
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
