package com.joinlivora.backend.chat;

import com.joinlivora.backend.monetization.PpvContent;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ppv_chat_access", uniqueConstraints = {
    @UniqueConstraint(name = "uk_ppv_chat_access_user_room_ppv", columnNames = {"user_id", "room_id", "ppv_content_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"creator", "roomId", "ppvContentId"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PPVChatAccess {

    @Id
    @GeneratedValue
    @UuidGenerator
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Stream roomId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ppv_content_id", nullable = false)
    private PpvContent ppvContentId;

    @Column(nullable = false, updatable = false)
    private Instant grantedAt;

    @Column
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }
}
