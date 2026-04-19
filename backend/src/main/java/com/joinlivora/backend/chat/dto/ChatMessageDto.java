package com.joinlivora.backend.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ChatMessageDto {
    private String id;
    private String type;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long senderId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String senderUsername;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String senderRole;
    private String username;
    private String content;
    private Integer amount;
    private Instant timestamp;
    private boolean systemMessage;
    private boolean isPaid;
    private String highlight;
    private String animationType;
    private String currency;
    private Long creatorUserId;
    private boolean isStreamOwner;
    private String senderType; // USER | CREATOR | OWNER | ADMIN | SYSTEM | BOT
    private String messageId; // Server-generated UUID for client-side deduplication

    // Highlight fields for Stripe intents
    private boolean isHighlighted;
    private com.joinlivora.backend.monetization.HighlightType highlightType;
    private String clientRequestId;
    private int highlightDuration;

    // Backward compatibility
    private UUID roomId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String sender;
    private String message;
}
