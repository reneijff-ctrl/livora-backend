package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.dto.ModerationSeverity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "chat_violation_logs", indexes = {
    @Index(name = "idx_chat_violation_user", columnList = "userId"),
    @Index(name = "idx_chat_violation_creator", columnList = "creatorId"),
    @Index(name = "idx_chat_violation_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatViolationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModerationSeverity severity;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private Long creatorId;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
