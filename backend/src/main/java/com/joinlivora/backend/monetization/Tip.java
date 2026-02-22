package com.joinlivora.backend.monetization;

import com.joinlivora.backend.streaming.StreamRoom;
import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tips", indexes = {
    @Index(name = "idx_tip_from_user", columnList = "from_user_id"),
    @Index(name = "idx_tip_creator", columnList = "creator_id"),
    @Index(name = "idx_tip_room_id", columnList = "room_id"),
    @Index(name = "idx_tip_client_request_id", columnList = "clientRequestId", unique = true)
})
@Getter
@Setter
@ToString(exclude = {"senderUserId", "creatorUserId", "room"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tip {

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
    @JoinColumn(name = "room_id")
    private StreamRoom room;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    private String message;

    @Column(unique = true)
    private String clientRequestId;

    private String stripePaymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
