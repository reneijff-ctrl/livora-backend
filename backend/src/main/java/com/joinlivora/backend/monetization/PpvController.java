package com.joinlivora.backend.monetization;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ppv")
@RequiredArgsConstructor
@Slf4j
public class PpvController {

    private final PpvService ppvService;
    private final UserService userService;

    @PostMapping("/{ppvId}/purchase")
    public ResponseEntity<?> purchasePpv(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID ppvId
    ) {
        try {
            User user = userService.getByEmail(userDetails.getUsername());
            String clientSecret = ppvService.createPurchaseIntent(user, ppvId);
            return ResponseEntity.ok(Map.of("clientSecret", clientSecret));
        } catch (Exception e) {
            log.error("MONETIZATION: Failed to create purchase intent for PPV {}", ppvId, e);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{ppvId}/access")
    public ResponseEntity<?> getAccess(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID ppvId
    ) {
        try {
            User user = userService.getByEmail(userDetails.getUsername());
            String accessUrl = ppvService.getAccessUrl(user, ppvId);
            return ResponseEntity.ok(Map.of("accessUrl", accessUrl));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
