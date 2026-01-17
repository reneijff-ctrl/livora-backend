package com.joinlivora.backend.token;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TipRecordRepository tipRecordRepository;

    @GetMapping("/balances")
    public ResponseEntity<List<TokenBalance>> getAllBalances() {
        return ResponseEntity.ok(tokenBalanceRepository.findAll());
    }

    @PostMapping("/adjust")
    public ResponseEntity<Void> adjustBalance(@RequestBody Map<String, Object> payload) {
        String email = (String) payload.get("email");
        Number amount = (Number) payload.get("amount");
        
        if (email == null || amount == null) {
            return ResponseEntity.badRequest().build();
        }

        User user = userService.getByEmail(email);
        tokenService.creditTokens(user, amount.longValue(), "Admin adjustment");
        
        log.info("ADMIN: Adjusted balance for {} by {}", email, amount);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/tips")
    public ResponseEntity<List<TipRecord>> getAllTips() {
        return ResponseEntity.ok(tipRecordRepository.findAll());
    }
}
