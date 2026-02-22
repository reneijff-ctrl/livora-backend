package com.joinlivora.backend.monetization;

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

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ppv")
@RequiredArgsConstructor
@Slf4j
public class PpvController {

    private final PpvService ppvService;
    private final PPVPurchaseService ppvPurchaseService;
    private final UserService userService;

    @PostMapping("/{ppvId}/purchase")
    public ResponseEntity<?> purchasePpv(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID ppvId,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) throws Exception {
        User user = userService.getByEmail(userDetails.getUsername());
        String ipAddress = RequestUtil.getClientIP(request);
        String country = RequestUtil.getClientCountry(request);
        String userAgent = RequestUtil.getUserAgent(request);
        String clientRequestId = body != null ? body.get("clientRequestId") : null;
        String clientSecret = ppvPurchaseService.createPurchaseIntent(user, ppvId, ipAddress, country, userAgent, clientRequestId);
        return ResponseEntity.ok(Map.of("clientSecret", clientSecret));
    }

    @GetMapping("/{ppvId}/access")
    public ResponseEntity<?> getAccess(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID ppvId
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        String accessUrl = ppvService.getAccessUrl(user, ppvId);
        return ResponseEntity.ok(Map.of("accessUrl", accessUrl));
    }
}
