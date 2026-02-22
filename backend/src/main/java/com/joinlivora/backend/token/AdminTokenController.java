package com.joinlivora.backend.token;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.wallet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/tokens")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminTokenController {

    private final TokenService tokenService;
    private final UserService userService;
    private final UserWalletRepository tokenBalanceRepository;
    private final TipRecordRepository tipRecordRepository;
    private final AuditService auditService;

    @GetMapping("/balances")
    public ResponseEntity<List<UserWallet>> getAllBalances() {
        return ResponseEntity.ok(tokenBalanceRepository.findAll());
    }

    @PostMapping("/adjust")
    public ResponseEntity<Void> adjustBalance(
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        String email = (String) payload.get("email");
        Number amount = (Number) payload.get("amount");
        
        if (email == null || amount == null) {
            return ResponseEntity.badRequest().build();
        }

        User user = userService.getByEmail(email);
        tokenService.creditTokens(user, amount.longValue(), "Admin adjustment");
        
        User admin = userService.getByEmail(adminDetails.getUsername());
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                "TOKEN_BALANCE_ADJUSTED",
                "USER",
                new UUID(0L, user.getId()),
                Map.of("amount", amount, "type", "Admin adjustment"),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        
        log.info("ADMIN: Adjusted balance for {} by {}", email, amount);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/tips")
    public ResponseEntity<List<TipRecord>> getAllTips() {
        return ResponseEntity.ok(tipRecordRepository.findAll());
    }
}
