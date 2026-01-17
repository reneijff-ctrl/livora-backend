package com.joinlivora.backend.monetization;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/tips")
@RequiredArgsConstructor
@Slf4j
public class TipController {

    private final TipService tipService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> createTipIntent(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> payload
    ) {
        try {
            User fromUser = userService.getByEmail(userDetails.getUsername());
            Long creatorId = Long.valueOf(payload.get("creatorId").toString());
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());
            String message = (String) payload.get("message");

            String clientSecret = tipService.createTipIntent(fromUser, creatorId, amount, message);
            return ResponseEntity.ok(Map.of("clientSecret", clientSecret));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("MONETIZATION: Failed to create tip intent", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Payment processing failed"));
        }
    }
}
