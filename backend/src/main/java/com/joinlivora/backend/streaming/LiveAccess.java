package com.joinlivora.backend.streaming;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * LiveAccess - Tracks temporary or permanent access granted to a viewer to watch a creator's stream.
 */
@Entity
@Table(name = "live_access", indexes = {
    @Index(name = "idx_live_access_lookup", columnList = "creator_user_id, viewer_user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_user_id", nullable = false)
    private Long creatorUserId;

    @Column(name = "viewer_user_id", nullable = false)
    private Long viewerUserId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
