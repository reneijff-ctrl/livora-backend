package com.joinlivora.backend.token;

import com.joinlivora.backend.payment.dto.CheckoutResponse;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.payment.PaymentService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
@Slf4j
public class TokenController {

    private final TokenService tokenService;
    private final UserService userService;
    private final PaymentService paymentService;
    private final TokenPackageRepository tokenPackageRepository;

    @GetMapping("/packages")
    public ResponseEntity<List<TokenPackage>> getPackages() {
        return ResponseEntity.ok(tokenService.getActivePackages());
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Long>> getBalance(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        TokenBalance balance = tokenService.getBalance(user);
        return ResponseEntity.ok(Map.of("balance", balance.getBalance()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TokenTransaction>> getTransactions(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(tokenService.getTransactionHistory(user));
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> createCheckoutSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> payload
    ) {
        String packageIdStr = payload.get("packageId");
        if (packageIdStr == null) {
            return ResponseEntity.badRequest().build();
        }

        UUID packageId = UUID.fromString(packageIdStr);
        TokenPackage tokenPackage = tokenPackageRepository.findByActiveTrueAndId(packageId)
                .orElseThrow(() -> new RuntimeException("Token package not found"));

        log.info("SECURITY: Token checkout requested for user: {} package: {}", userDetails.getUsername(), packageId);
        User user = userService.getByEmail(userDetails.getUsername());

        try {
            String checkoutUrl = paymentService.createTokenCheckoutSession(user, tokenPackage.getStripePriceId(), packageId);
            return ResponseEntity.ok(new CheckoutResponse(checkoutUrl));
        } catch (StripeException e) {
            log.error("SECURITY: Failed to create Stripe token checkout session", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/tip")
    public ResponseEntity<Map<String, String>> sendTip(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> payload
    ) {
        String roomIdStr = (String) payload.get("roomId");
        Number amountNum = (Number) payload.get("amount");
        
        if (roomIdStr == null || amountNum == null) {
            return ResponseEntity.badRequest().build();
        }

        UUID roomId = UUID.fromString(roomIdStr);
        long amount = amountNum.longValue();

        User user = userService.getByEmail(userDetails.getUsername());
        try {
            tokenService.sendTip(user, roomId, amount);
            return ResponseEntity.ok(Map.of("message", "Tip sent successfully"));
        } catch (Exception e) {
            log.error("TOKEN: Tip failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
