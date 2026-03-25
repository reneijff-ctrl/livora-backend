package com.joinlivora.backend.chat.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModerateResult {
    private boolean allowed;
    private String reason;
    private ModerationSeverity severity;
    private boolean positive;

    public static ModerateResult allowed() {
        return ModerateResult.builder()
                .allowed(true)
                .build();
    }

    public static ModerateResult allowed(boolean positive) {
        return ModerateResult.builder()
                .allowed(true)
                .positive(positive)
                .build();
    }

    public static ModerateResult blocked(String reason, ModerationSeverity severity) {
        return ModerateResult.builder()
                .allowed(false)
                .reason(reason)
                .severity(severity)
                .build();
    }
}
