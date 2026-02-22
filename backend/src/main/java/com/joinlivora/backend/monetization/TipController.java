package com.joinlivora.backend.monetization;

import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.monetization.dto.TipResult;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tips")
@RequiredArgsConstructor
@Slf4j
public class TipController {

    private final TipService tipService;
    private final UserService userService;

    @PostMapping("/intent")
    public ResponseEntity<?> createTipIntent(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) throws Exception {
        User fromUser = userService.getByEmail(userDetails.getUsername());
        Long userId = Long.valueOf(payload.get("creator").toString());
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        String message = (String) payload.get("message");
        String clientRequestId = (String) payload.get("clientRequestId");
        String ipAddress = RequestUtil.getClientIP(request);
        String country = RequestUtil.getClientCountry(request);
        String userAgent = RequestUtil.getUserAgent(request);
        String fingerprint = RequestUtil.getDeviceFingerprint(request);

        String clientSecret = tipService.createTipIntent(fromUser, userId, amount, message, clientRequestId, ipAddress, country, userAgent, fingerprint);
        return ResponseEntity.ok(Map.of("clientSecret", clientSecret));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createTestTip(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> payload
    ) throws Exception {
        User fromUser = userService.getByEmail(userDetails.getUsername());
        Long creatorId = Long.valueOf(payload.get("creator").toString());
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        String clientSecret = tipService.createTestTip(fromUser, creatorId, amount);
        return ResponseEntity.ok(Map.of("clientSecret", clientSecret));
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendTokenTip(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) throws Exception {
        User viewer = userService.getByEmail(userDetails.getUsername());
        UUID roomId = UUID.fromString(payload.get("roomId").toString());
        long amount = Long.parseLong(payload.get("amount").toString());
        String message = (String) payload.get("message");
        String clientRequestId = (String) payload.get("clientRequestId");
        String ipAddress = RequestUtil.getClientIP(request);
        String fingerprint = RequestUtil.getDeviceFingerprint(request);

        TipResult result = tipService.sendTokenTip(viewer, roomId, amount, message, clientRequestId, ipAddress, fingerprint);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/preview")
    public ResponseEntity<?> previewTip(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> payload
    ) {
        log.info("MONETIZATION: Tip preview requested by {}", userDetails.getUsername());
        // Simply return success as requested
        return ResponseEntity.ok(Map.of(
            "message", "Tip preview successful",
            "status", "PREVIEW_OK"
        ));
    }
}
