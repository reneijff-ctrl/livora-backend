package com.joinlivora.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStreamDTO {
    private UUID streamId;
    private Long creatorId;
    private Long userId;     // Added for compatibility with AdminLiveStreamsWidget.tsx
    private Long creator;    // Added for compatibility with AdminLiveStreamsWidget.tsx
    private String creatorUsername;
    private String title;
    private int viewerCount;
    private Instant startedAt;
    private long durationSeconds;
    private int fraudRiskScore;
    private int messageRate; // Messages per second

    // Private session info for admin dashboard
    private boolean privateActive;
    private Long privatePricePerMinute;
    private boolean spyEnabled;
    private int activeSpyCount;
}
