package com.joinlivora.backend.moderation;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moderation_audit_log", indexes = {
    @Index(name = "idx_audit_creator", columnList = "creator_id"),
    @Index(name = "idx_audit_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "action_type", nullable = false)
    private String actionType; // MUTE, SHADOW_MUTE, KICK, BAN, UNBAN, GRANT_MOD, REVOKE_MOD, DELETE_MESSAGE

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "target_username")
    private String targetUsername;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_username")
    private String actorUsername;

    @Column(name = "actor_role", nullable = false)
    private String actorRole; // CREATOR, MODERATOR, ADMIN

    @Column
    private String metadata; // JSON: duration, reason, messageId, etc.

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
