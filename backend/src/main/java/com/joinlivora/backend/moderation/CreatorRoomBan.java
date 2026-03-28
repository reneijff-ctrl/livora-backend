package com.joinlivora.backend.moderation;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_room_bans", indexes = {
    @Index(name = "idx_room_ban_creator", columnList = "creator_id"),
    @Index(name = "idx_room_ban_target", columnList = "target_user_id"),
    @Index(name = "idx_room_ban_creator_target", columnList = "creator_id, target_user_id"),
    @Index(name = "idx_room_ban_expires", columnList = "expires_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorRoomBan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_user_id", nullable = false)
    private User issuedBy;

    @Column(nullable = false)
    private String banType; // "5m", "30m", "24h", "permanent"

    @Column
    private String reason;

    @Column(name = "expires_at")
    private Instant expiresAt; // null for permanent bans

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        active = true;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isEffective() {
        return active && !isExpired();
    }
}
