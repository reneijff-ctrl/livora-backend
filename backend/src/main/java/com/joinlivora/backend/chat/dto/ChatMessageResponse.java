package com.joinlivora.backend.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private String messageId;
    private String type;
    private Long senderId;
    private String senderUsername;
    private String senderRole;
    private String content;
    private Integer amount;
    private Instant timestamp;
    private String animationType;
    private String rarity;
    private String giftName;
    private String soundProfile;
}
