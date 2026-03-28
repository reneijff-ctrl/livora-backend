package com.joinlivora.backend.privateshow;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "private_spy_sessions", indexes = {
    @Index(name = "idx_spy_session_status", columnList = "status"),
    @Index(name = "idx_spy_session_parent", columnList = "private_session_id"),
    @Index(name = "idx_spy_session_viewer", columnList = "spy_viewer_id")
})
@Getter
@Setter
@ToString(exclude = {"privateSession", "spyViewer"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivateSpySession {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "private_session_id", nullable = false)
    private PrivateSession privateSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spy_viewer_id", nullable = false)
    private User spyViewer;

    @Column(name = "spy_price_per_minute", nullable = false)
    private long spyPricePerMinute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpySessionStatus status;

    private Instant startedAt;
    private Instant endedAt;
    private Instant lastBilledAt;

    @Column(name = "end_reason")
    private String endReason;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}
