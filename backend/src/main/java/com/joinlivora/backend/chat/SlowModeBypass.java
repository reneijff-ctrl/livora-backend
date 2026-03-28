package com.joinlivora.backend.chat;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.streaming.Stream;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slow_mode_bypass", uniqueConstraints = {
    @UniqueConstraint(name = "uk_slow_mode_bypass_user_room", columnNames = {"user_id", "room_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"userId", "roomId"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SlowModeBypass {

    @Id
    @GeneratedValue
    @UuidGenerator
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Stream roomId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlowModeBypassSource source;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
