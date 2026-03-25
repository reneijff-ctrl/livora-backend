package com.joinlivora.backend.chat.dto;

import com.joinlivora.backend.chat.domain.ChatRoom;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDto {
    private UUID id;
    private String name;
    private boolean isLive;
    private boolean ppvRequired;
    private boolean isPaid;
    private Long pricePerMessage;
    private Long creatorId;

    public static ChatRoomDto fromEntity(ChatRoom chatRoom) {
        if (chatRoom == null) return null;
        return ChatRoomDto.builder()
                .id(new UUID(0, chatRoom.getId()))
                .name(chatRoom.getName())
                .isLive(chatRoom.isLive())
                .ppvRequired(chatRoom.getPpvContent() != null)
                .isPaid(chatRoom.isPaid())
                .pricePerMessage(chatRoom.getPricePerMessage())
                .creatorId(chatRoom.getCreatorId())
                .build();
    }

    /**
     * @deprecated Use fromEntity(com.joinlivora.backend.chat.domain.ChatRoom) instead.
     */
    @Deprecated
    public static ChatRoomDto fromEntity(com.joinlivora.backend.chat.ChatRoom chatRoom) {
        if (chatRoom == null) return null;
        return ChatRoomDto.builder()
                .id(chatRoom.getId())
                .name(chatRoom.getName())
                .isLive(chatRoom.isLive())
                .ppvRequired(chatRoom.isPpvRoom())
                .isPaid(chatRoom.isPaid())
                .pricePerMessage(chatRoom.getPricePerMessage())
                .creatorId(chatRoom.getCreatedBy() != null ? chatRoom.getCreatedBy().getId() : null)
                .build();
    }
}
