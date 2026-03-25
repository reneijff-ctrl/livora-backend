package com.joinlivora.backend.chat;

import lombok.Data;

@Data
public class MuteRequest {
    private Long userId;
    private Long durationSeconds;
    private String roomId;
}
