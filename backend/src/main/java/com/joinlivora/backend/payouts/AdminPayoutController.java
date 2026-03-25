package com.joinlivora.backend.payouts;

import com.joinlivora.backend.payouts.dto.FreezeRequest;
import com.joinlivora.backend.payouts.service.PayoutFreezeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController("payoutsAdminPayoutController")
@RequestMapping("/admin/payouts")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminPayoutController {

    private final PayoutFreezeService payoutFreezeService;
    private final com.joinlivora.backend.user.UserService userService;

    @PostMapping("/{creatorId}/freeze")
    public ResponseEntity<?> freeze(@PathVariable UUID creatorId, @RequestBody(required = false) FreezeRequest request, Principal principal) {
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Manual admin freeze";
        String adminPrincipalName = principal.getName();
        
        log.info("ADMIN: Request to freeze payouts for creator {} by admin {}", creatorId, adminPrincipalName);
        payoutFreezeService.freezeCreator(creatorId, reason, adminPrincipalName);
        
        return ResponseEntity.ok(Map.of("message", "Payouts frozen for creator " + creatorId));
    }

    @PostMapping("/{creatorId}/unfreeze")
    public ResponseEntity<?> unfreeze(@PathVariable UUID creatorId, Principal principal) {
        String adminPrincipalName = principal.getName();
        
        log.info("ADMIN: Request to unfreeze payouts for creator {} by admin {}", creatorId, adminPrincipalName);
        payoutFreezeService.unfreezeCreator(creatorId, adminPrincipalName);
        
        return ResponseEntity.ok(Map.of("message", "Payouts unfrozen for creator " + creatorId));
    }
}
