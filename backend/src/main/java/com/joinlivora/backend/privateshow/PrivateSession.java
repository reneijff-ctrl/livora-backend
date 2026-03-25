package com.joinlivora.backend.privateshow;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "private_sessions", indexes = {
    @Index(name = "idx_private_session_viewer", columnList = "viewer_id"),
    @Index(name = "idx_private_session_creator", columnList = "creator_id"),
    @Index(name = "idx_private_session_status", columnList = "status")
})
@Getter
@Setter
@ToString(exclude = {"viewer", "creator"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivateSession {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viewer_id", nullable = false)
    private User viewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false)
    private long pricePerMinute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrivateSessionStatus status;

    private Instant requestedAt;
    private Instant acceptedAt;
    private Instant rejectedAt;
    private Instant startedAt;
    private Instant endedAt;
    private Instant lastBilledAt;

    private String endReason;

    @com.fasterxml.jackson.annotation.JsonProperty("creator")
    public Long getUserId() {
        return creator != null ? creator.getId() : null;
    }

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}
