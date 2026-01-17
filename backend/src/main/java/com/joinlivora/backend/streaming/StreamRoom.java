package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stream_rooms", indexes = {
    @Index(name = "idx_stream_room_creator", columnList = "creator_id"),
    @Index(name = "idx_stream_room_live", columnList = "is_live")
})
@Getter
@Setter
@ToString(exclude = "creator")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamRoom {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Builder.Default
    @Column(nullable = false)
    private boolean isLive = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean isPremium = false;

    @Builder.Default
    @Column(nullable = false)
    private int viewerCount = 0;

    private String streamTitle;

    private String description;

    private Instant endedAt;

    private Long minChatTokens;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant startedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
