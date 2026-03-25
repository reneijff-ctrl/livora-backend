package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.dto.ChatRoomDto;
import com.joinlivora.backend.chat.dto.ChatRoomResponse;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final UserService userService;

    @GetMapping("/live")
    public List<ChatRoomDto> getLiveRooms() {
        return chatRoomService.getLiveRooms();
    }

    @GetMapping("/{roomId}")
    public ChatRoomDto getRoom(@PathVariable UUID roomId) {
        return chatRoomService.getRoom(roomId);
    }

    @GetMapping("/old/{roomId}")
    public ChatRoomResponse getRoomOld(@PathVariable UUID roomId, Principal principal) {
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        com.joinlivora.backend.chat.domain.ChatRoom room = chatRoomService.getRoomEntity(roomId);
        chatRoomService.validateAccess(room.getName(), user.getId());
        return ChatRoomResponse.fromEntity(room, user);
    }

    @GetMapping("/ppv/{ppvContentId}")
    public ChatRoomResponse getRoomByPpv(@PathVariable UUID ppvContentId, Principal principal) {
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        com.joinlivora.backend.chat.domain.ChatRoom room = chatRoomService.getRoomByPpv(ppvContentId);
        chatRoomService.validateAccess(room.getName(), user.getId());
        return ChatRoomResponse.fromEntity(room, user);
    }

    @PostMapping("/ppv/{ppvContentId}")
    public ChatRoomResponse createPpvRoom(@PathVariable UUID ppvContentId, Principal principal) {
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        return ChatRoomResponse.fromEntity(chatRoomService.createPpvChatRoom(ppvContentId, user.getId()), user);
    }
}
