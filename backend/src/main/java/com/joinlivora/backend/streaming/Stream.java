package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "streams", indexes = {
    @Index(name = "idx_streams_creator_id", columnList = "creator_id"),
    @Index(name = "idx_streams_is_live", columnList = "is_live"),
    @Index(name = "idx_streams_mediasoup_room_id", columnList = "mediasoup_room_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stream {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    private String title;
    
    @Transient
    @Builder.Default
    private int viewerCount = 0;

    @Column(name = "is_live", nullable = false)
    @Builder.Default
    private boolean isLive = false;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "mediasoup_room_id")
    private UUID mediasoupRoomId;

    @Column(name = "stream_key", unique = true)
    private String streamKey;

    @Column(name = "is_paid", nullable = false)
    @Builder.Default
    private boolean isPaid = false;

    @Column(name = "admission_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private java.math.BigDecimal admissionPrice = java.math.BigDecimal.ZERO;

    @Column(name = "chat_enabled", nullable = false)
    @Builder.Default
    private boolean chatEnabled = true;

    @Column(name = "slow_mode", nullable = false)
    @Builder.Default
    private boolean slowMode = false;

    @Column(name = "slow_mode_interval")
    private Integer slowModeInterval;

    @Column(name = "max_viewers")
    private Integer maxViewers;

    @Column(name = "stream_category")
    private String streamCategory;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
