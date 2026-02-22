package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.CheckoutResponse;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.util.RequestUtil;
import com.stripe.exception.StripeException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> createCheckoutSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @org.springframework.web.bind.annotation.RequestBody(required = false) com.joinlivora.backend.payment.dto.CheckoutRequest checkoutRequest,
            HttpServletRequest request
    ) throws com.stripe.exception.StripeException {
        log.info("SECURITY: Checkout session requested for creator: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        String ipAddress = RequestUtil.getClientIP(request);
        String country = RequestUtil.getClientCountry(request);
        String userAgent = RequestUtil.getUserAgent(request);
        String fingerprint = RequestUtil.getDeviceFingerprint(request);
        
        String planId = (checkoutRequest != null) ? checkoutRequest.getPlanId() : null;

        String checkoutUrl = paymentService.createCheckoutSession(user, planId, ipAddress, country, userAgent, fingerprint);
        return ResponseEntity.ok(new CheckoutResponse(checkoutUrl));
    }
}
