package com.joinlivora.backend.livestream.domain;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "livestream_sessions", indexes = {
    @Index(name = "idx_livestream_session_creator", columnList = "creator_id"),
    @Index(name = "idx_livestream_session_status", columnList = "status"),
    @Index(name = "idx_livestream_session_started_at", columnList = "started_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LivestreamSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LivestreamStatus status = LivestreamStatus.SCHEDULED;

    @Column(name = "is_paid", nullable = false)
    private boolean isPaid;

    @Column(name = "admission_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal admissionPrice = BigDecimal.ZERO;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "stream_key")
    private String streamKey;

    @Version
    @Builder.Default
    private Long version = 0L;

    // --- Business Methods ---

    public void start() {
        if (this.status == LivestreamStatus.ENDED) {
            throw new IllegalStateException("Cannot restart an ended session");
        }
        this.status = LivestreamStatus.LIVE;
        this.startedAt = LocalDateTime.now();
    }

    public void end() {
        this.status = LivestreamStatus.ENDED;
        this.endedAt = LocalDateTime.now();
    }

    public boolean isLive() {
        return this.status == LivestreamStatus.LIVE;
    }

    public boolean isFree() {
        return !this.isPaid;
    }
}
