package com.joinlivora.backend.content.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockResponse {
    private boolean unlocked;
    private long remainingTokens;
}
