package com.joinlivora.backend.chat;

import lombok.Data;
import java.util.UUID;

@Data
public class RevokeBypassRequest {
    private Long userId;
    private UUID roomId;
}
