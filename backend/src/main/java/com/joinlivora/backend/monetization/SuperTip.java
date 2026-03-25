package com.joinlivora.backend.monetization;

import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "super_tips", indexes = {
    @Index(name = "idx_supertip_room", columnList = "room_id"),
    @Index(name = "idx_supertip_created_at", columnList = "created_at"),
    @Index(name = "idx_supertip_client_request_id", columnList = "client_request_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"senderUserId", "creatorUserId", "roomId"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SuperTip {

    @Id
    @GeneratedValue
    @UuidGenerator
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User senderUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creatorUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Stream roomId;

    @Column(nullable = false)
    private BigDecimal amount;

    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HighlightLevel highlightLevel;

    @Column(nullable = false)
    private int durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipStatus status;

    @Column(name = "client_request_id", unique = true)
    private String clientRequestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
