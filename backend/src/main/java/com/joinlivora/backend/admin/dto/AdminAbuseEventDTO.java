package com.joinlivora.backend.admin.dto;

import java.time.Instant;
import java.util.UUID;

public class AdminAbuseEventDTO {

    private String type;
    private UUID streamId;
    private String creatorUsername;
    private String description;
    private Instant timestamp;

    public AdminAbuseEventDTO(
        String type,
        UUID streamId,
        String creatorUsername,
        String description
    ) {
        this.type = type;
        this.streamId = streamId;
        this.creatorUsername = creatorUsername;
        this.description = description;
        this.timestamp = Instant.now();
    }

    public String getType() { return type; }
    public UUID getStreamId() { return streamId; }
    public String getCreatorUsername() { return creatorUsername; }
    public String getDescription() { return description; }
    public Instant getTimestamp() { return timestamp; }
}