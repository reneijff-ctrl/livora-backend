package com.joinlivora.backend.streaming;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "creator_moderation_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationSettings {

    @Id
    @Column(name = "creator_user_id")
    private Long creatorUserId;

    @Column(name = "auto_pin_large_tips", nullable = false)
    @Builder.Default
    private boolean autoPinLargeTips = true;

    @Column(name = "ai_highlight_enabled", nullable = false)
    @Builder.Default
    private boolean aiHighlightEnabled = true;

    @Column(name = "strict_mode", nullable = false)
    @Builder.Default
    private boolean strictMode = false;

    @Column(name = "banned_words", columnDefinition = "TEXT")
    @Builder.Default
    private String bannedWords = "";
}
