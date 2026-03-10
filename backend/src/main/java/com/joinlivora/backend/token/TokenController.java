package com.joinlivora.backend.token;

import com.joinlivora.backend.monetization.TipOrchestrationService;
import com.joinlivora.backend.monetization.dto.TipResult;
import com.joinlivora.backend.token.dto.TokenTipRequest;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.payment.PaymentService;
import com.joinlivora.backend.payment.dto.CheckoutResponse;
import com.joinlivora.backend.wallet.*;
import com.joinlivora.backend.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
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
    private final TipOrchestrationService tipService;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;

    @GetMapping("/packages")
    public ResponseEntity<List<TokenPackage>> getPackages() {
        return ResponseEntity.ok(tokenService.getActivePackages());
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Long>> getBalance(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        UserWallet balance = tokenService.getBalance(user);
        return ResponseEntity.ok(Map.of("balance", balance.getBalance()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<WalletTransaction>> getTransactions(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(tokenService.getTransactionHistory(user));
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> createCheckoutSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> payload,
            HttpServletRequest request
    ) throws com.stripe.exception.StripeException {
        String packageIdStr = payload.get("packageId");
        if (packageIdStr == null) {
            return ResponseEntity.badRequest().build();
        }

        UUID packageId = UUID.fromString(packageIdStr);
        TokenPackage tokenPackage = tokenPackageRepository.findByActiveTrueAndId(packageId)
                .orElseThrow(() -> new RuntimeException("Token package not found"));

        log.info("SECURITY: Token checkout requested for creator: {} package: {}", userDetails.getUsername(), packageId);
        User user = userService.getByEmail(userDetails.getUsername());
        String ipAddress = RequestUtil.getClientIP(request);
        String country = RequestUtil.getClientCountry(request);
        String userAgent = RequestUtil.getUserAgent(request);
        String fingerprint = RequestUtil.getDeviceFingerprint(request);

        String checkoutUrl = paymentService.createTokenCheckoutSession(user, tokenPackage.getStripePriceId(), packageId, ipAddress, country, userAgent, fingerprint);
        return ResponseEntity.ok(new CheckoutResponse(checkoutUrl));
    }

    @PostMapping("/tip")
    public ResponseEntity<?> sendTip(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TokenTipRequest tipRequest,
            HttpServletRequest request
    ) {
        if (tipRequest.getAmount() == null || tipRequest.getAmount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Amount must be greater than 0"));
        }

        Long creatorUserId = tipRequest.getCreatorId();
        if (creatorUserId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Creator ID is required"));
        }

        UUID roomId = streamRepository.findByCreatorIdAndIsLiveTrue(creatorUserId)
                .map(com.joinlivora.backend.streaming.Stream::getId)
                .orElse(null);

        if (roomId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Creator does not have an active stream room"));
        }

        User user = userService.getByEmail(userDetails.getUsername());
        String ipAddress = RequestUtil.getClientIP(request);
        String fingerprint = RequestUtil.getDeviceFingerprint(request);

        try {
            TipResult result = tipService.sendTokenTip(
                    user,
                    roomId,
                    tipRequest.getAmount().longValue(),
                    tipRequest.getMessage(),
                    tipRequest.getClientRequestId(),
                    ipAddress,
                    fingerprint,
                    tipRequest.getGiftName()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("TOKEN: Tip failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
