package com.joinlivora.backend.livestream.dto;

import com.joinlivora.backend.livestream.domain.LiveStreamState;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LiveStreamResponse {
    private UUID id;
    private Long creatorUserId;
    private LiveStreamState status;
    private Instant startedAt;
    private Instant endedAt;
    private UUID streamId;
    private UUID roomId;
    private UUID streamRoomId;
    private String streamKey;
    private boolean isLive;
    private String title;
    private String thumbnailUrl;
}
