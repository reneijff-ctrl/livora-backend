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
@Table(name = "highlighted_messages", indexes = {
    @Index(name = "idx_highlighted_msg_message_id", columnList = "message_id"),
    @Index(name = "idx_highlighted_msg_user_id", columnList = "user_id"),
    @Index(name = "idx_highlighted_msg_room_id", columnList = "room_id"),
    @Index(name = "idx_highlighted_msg_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HighlightedMessage {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Stream roomId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HighlightType highlightType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipStatus status;

    private String stripePaymentIntentId;

    @Column(unique = true)
    private String clientRequestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private boolean moderated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by")
    private User moderatedBy;

    private Instant moderatedAt;

    private String moderationReason;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
