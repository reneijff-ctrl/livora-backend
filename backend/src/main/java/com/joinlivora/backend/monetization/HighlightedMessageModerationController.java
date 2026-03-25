package com.joinlivora.backend.monetization;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/moderation/highlights")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
public class HighlightedMessageModerationController {

    private final HighlightedMessageService highlightedMessageService;
    private final UserService userService;

    @PostMapping("/{id}/remove")
    public ResponseEntity<?> removeHighlight(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails moderatorDetails,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        try {
            User moderator = userService.getByEmail(moderatorDetails.getUsername());
            String reason = "No reason provided";
            if (payload != null) {
                reason = payload.getOrDefault("reason", payload.getOrDefault("type", "No reason provided"));
            }
            highlightedMessageService.removeHighlight(id, moderator, reason);
            return ResponseEntity.ok(Map.of("message", "Highlight removed successfully"));
        } catch (Exception e) {
            log.error("MODERATION: Failed to remove highlight {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> refundHighlight(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails moderatorDetails,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        try {
            User moderator = userService.getByEmail(moderatorDetails.getUsername());
            String reason = "No reason provided";
            if (payload != null) {
                reason = payload.getOrDefault("reason", payload.getOrDefault("type", "No reason provided"));
            }
            highlightedMessageService.refundHighlight(id, moderator, reason);
            return ResponseEntity.ok(Map.of("message", "Highlight refunded successfully"));
        } catch (Exception e) {
            log.error("MODERATION: Failed to refund highlight {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
