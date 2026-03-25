package com.joinlivora.backend.chat;

import lombok.Data;

@Data
public class DeleteRequest {
    private String roomId;
    private String messageId;
}
