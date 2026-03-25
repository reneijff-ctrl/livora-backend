package com.joinlivora.backend.chat.domain;

import com.joinlivora.backend.chat.ChatMode;
import com.joinlivora.backend.monetization.PpvContent;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity(name = "ChatRoomV2")
@Table(name = "chat_rooms_v2", indexes = {
    @Index(name = "idx_chatroom_creator_v2", columnList = "creator_id"),
    @Index(name = "idx_chatroom_is_live_v2", columnList = "is_live"),
    @Index(name = "idx_chatroom_created_at_v2", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(unique = true)
    private String name;

    @Column(name = "is_live", nullable = false)
    private boolean isLive;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ChatRoomStatus status = ChatRoomStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean isPrivate = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_mode", nullable = false)
    @Builder.Default
    private ChatMode chatMode = ChatMode.PUBLIC;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ppv_content_id")
    private PpvContent ppvContent;

    @Column(nullable = false)
    @Builder.Default
    private boolean isPaid = false;

    private Long pricePerMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false)
    @Builder.Default
    private ChatRoomType roomType = ChatRoomType.STREAM;

    @Column(name = "viewer_id")
    private Long viewerId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
