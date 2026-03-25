package com.joinlivora.backend.chat;

import lombok.Data;

@Data
public class BanRequest {
    private Long userId;
    private String roomId;
}
