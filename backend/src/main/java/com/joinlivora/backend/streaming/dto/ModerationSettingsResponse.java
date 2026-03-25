package com.joinlivora.backend.streaming.dto;

import com.joinlivora.backend.streaming.ModerationSettings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationSettingsResponse {
    private Long creatorUserId;
    private boolean autoPinLargeTips;
    private boolean aiHighlightEnabled;
    private boolean strictMode;
    private String bannedWords;

    public ModerationSettingsResponse(ModerationSettings settings) {
        this.creatorUserId = settings.getCreatorUserId();
        this.autoPinLargeTips = settings.isAutoPinLargeTips();
        this.aiHighlightEnabled = settings.isAiHighlightEnabled();
        this.strictMode = settings.isStrictMode();
        this.bannedWords = settings.getBannedWords();
    }
}
