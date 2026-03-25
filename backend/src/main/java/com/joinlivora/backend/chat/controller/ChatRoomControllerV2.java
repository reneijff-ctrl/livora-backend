package com.joinlivora.backend.chat.controller;

import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomControllerV2 {

    private final ChatRoomService chatRoomService;

    @GetMapping("/creator/{creatorUserId}")
    public ChatRoom getRoomForCreator(@PathVariable Long creatorUserId) {
        return chatRoomService.getOrCreateRoom(creatorUserId);
    }
}
