package com.joinlivora.backend.chat;

import com.joinlivora.backend.monetization.PpvContent;
import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * @deprecated Use com.joinlivora.backend.chat.domain.ChatRoom instead.
 * TODO: Remove this class after migrating legacy data from chat_rooms to chat_rooms_v2 table.
 */
@Deprecated
@Entity
@Table(name = "chat_rooms", indexes = {
    @Index(name = "idx_chat_room_name", columnList = "name", unique = true),
    @Index(name = "idx_chat_room_creator", columnList = "created_by"),
    @Index(name = "idx_chat_room_is_live", columnList = "is_live")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private boolean isPrivate;

    @Column(name = "is_live", nullable = false)
    @Builder.Default
    private boolean isLive = false;

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

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Backward compatibility for ppvContentId
    public UUID getPpvContentId() {
        return ppvContent != null ? ppvContent.getId() : null;
    }

    public boolean isPpvRoom() {
        return ppvContent != null;
    }

    public boolean isRequiresPurchase() {
        return ppvContent != null;
    }

    public void setPpvContentId(UUID ppvContentId) {
        if (ppvContentId == null) {
            this.ppvContent = null;
        } else {
            // Note: This creates a transient entity with only ID set. 
            // In a managed context, it's better to use entityManager.getReferenceId()
            this.ppvContent = PpvContent.builder().id(ppvContentId).build();
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Custom builder to support ppvContentId for backward compatibility
    public static class ChatRoomBuilder {
        public ChatRoomBuilder ppvContentId(UUID ppvContentId) {
            if (ppvContentId != null) {
                this.ppvContent = PpvContent.builder().id(ppvContentId).build();
            }
            return this;
        }
    }
}
