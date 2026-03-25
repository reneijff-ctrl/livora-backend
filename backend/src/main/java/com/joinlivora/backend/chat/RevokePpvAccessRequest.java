package com.joinlivora.backend.chat;

import lombok.Data;
import java.util.UUID;

@Data
public class RevokePpvAccessRequest {
    private Long userId;
    private UUID roomId;
}
