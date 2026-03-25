package com.joinlivora.backend.chat;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class GrantPpvAccessRequest {
    private Long userId;
    private UUID roomId;
    private Instant expiresAt;
}
