package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.dto.ChatPpvAccessResponse;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/chat/ppv-access")
@RequiredArgsConstructor
public class ChatAccessController {

    private final ChatRoomService chatRoomService;
    private final UserService userService;

    @GetMapping("/{roomId}")
    public ChatPpvAccessResponse getPpvAccess(@PathVariable java.util.UUID roomId, Principal principal) {
        Long userId = null;
        if (principal != null) {
            User user = userService.resolveUserFromSubject(principal.getName())
                    .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
            userId = user.getId();
        }
        return chatRoomService.checkPpvAccess(roomId, userId);
    }
}
