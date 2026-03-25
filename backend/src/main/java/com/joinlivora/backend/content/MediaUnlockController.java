package com.joinlivora.backend.content;

import com.joinlivora.backend.content.dto.UnlockResponse;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaUnlockController {

    private final MediaUnlockService mediaUnlockService;
    private final UserService userService;

    @PostMapping("/{id}/unlock")
    public ResponseEntity<UnlockResponse> unlockMedia(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(mediaUnlockService.unlockMedia(user, id));
    }
}
