package com.joinlivora.backend.token;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.streaming.Stream;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tip_records", indexes = {
    @Index(name = "idx_tip_room", columnList = "room_id"),
    @Index(name = "idx_tip_viewer", columnList = "viewer_id"),
    @Index(name = "idx_tip_creator", columnList = "creator_id")
})
@Getter
@Setter
@ToString(exclude = {"viewer", "creator", "room"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viewer_id", nullable = false)
    private User viewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Stream room;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long creatorEarningTokens;

    @Column(nullable = false)
    private long platformFeeTokens;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
