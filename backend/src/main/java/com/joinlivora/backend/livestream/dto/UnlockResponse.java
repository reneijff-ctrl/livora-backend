package com.joinlivora.backend.livestream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockResponse {
    private boolean success;
    private long remainingTokens;
}
