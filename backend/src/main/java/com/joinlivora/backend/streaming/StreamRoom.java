package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
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

    @org.hibernate.annotations.Formula("(SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM livestream_sessions ls WHERE ls.creator_id = creator_id AND ls.status = 'LIVE')")
    private boolean isLive;

    @Builder.Default
    @Column(nullable = false)
    private boolean isPremium = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean slowMode = false;

    @Builder.Default
    @Column(nullable = false)
    private int viewerCount = 0;

    private String streamTitle;

    private String description;

    private Instant endedAt;

    private Long minChatTokens;

    @Builder.Default
    private boolean isPaid = false;

    private Long pricePerMessage;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal admissionPrice = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant startedAt;

    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    public Long getUserId() {
        return creator != null ? creator.getId() : null;
    }

    @org.hibernate.annotations.Formula("(SELECT c.id FROM creator_records c WHERE c.user_id = creator_id)")
    private Long creatorRecordId;

    @com.fasterxml.jackson.annotation.JsonProperty("creatorRecordId")
    public Long getCreatorRecordId() {
        return creatorRecordId;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("creator")
    public Long getCreatorId() {
        return creator != null ? creator.getId() : null;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
