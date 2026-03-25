package com.joinlivora.backend.chat.analysis;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SentimentResult {
    private double score; // -1 to +1
    private boolean positive;
    private boolean toxic;
}
