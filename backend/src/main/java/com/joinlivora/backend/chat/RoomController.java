package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.dto.ChatModeRequest;
import com.joinlivora.backend.chat.dto.ChatRoomResponse;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final ChatRoomService chatRoomService;
    private final UserService userService;

    @PutMapping("/{roomId}/chat-mode")
    public ChatRoomResponse updateChatMode(
            @PathVariable java.util.UUID roomId,
            @Valid @RequestBody ChatModeRequest request,
            Principal principal
    ) {
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        com.joinlivora.backend.chat.domain.ChatRoom room = chatRoomService.updateChatMode(roomId, request.getChatMode(), user.getId());
        return ChatRoomResponse.fromEntity(room, user);
    }

    @GetMapping("/{roomId}")
    public ChatRoomResponse getRoom(@PathVariable java.util.UUID roomId, Principal principal) {
        User user = (principal != null) ? userService.resolveUserFromSubject(principal.getName()).orElse(null) : null;
        com.joinlivora.backend.chat.domain.ChatRoom room = chatRoomService.getRoomEntity(roomId);
        return ChatRoomResponse.fromEntity(room, user);
    }
}
