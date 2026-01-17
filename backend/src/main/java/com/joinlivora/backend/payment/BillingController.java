package com.joinlivora.backend.payment;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingController {

    private final PaymentRepository paymentRepository;
    private final UserService userService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/portal")
    public ResponseEntity<Map<String, String>> openBillingPortal(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("SECURITY: Billing portal requested for user: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        try {
            String portalUrl = subscriptionService.createBillingPortalSession(user);
            return ResponseEntity.ok(Map.of("url", portalUrl));
        } catch (Exception e) {
            log.error("SECURITY: Failed to create billing portal session for user: {}", user.getEmail(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<Map<String, Object>>> getInvoices(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("SECURITY: Fetching invoices for user: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        
        List<Payment> payments = paymentRepository.findAllByUserOrderByCreatedAtDesc(user);
        
        List<Map<String, Object>> invoices = payments.stream().map(payment -> {
            Map<String, Object> invoice = new java.util.HashMap<>();
            invoice.put("id", payment.getId().toString());
            invoice.put("amount", payment.getAmount().multiply(new java.math.BigDecimal(100)).intValue());
            invoice.put("currency", payment.getCurrency().toUpperCase());
            invoice.put("status", "PAID");
            invoice.put("date", payment.getCreatedAt().toString());
            invoice.put("pdfUrl", payment.getReceiptUrl() != null ? payment.getReceiptUrl() : "#");
            return invoice;
        }).toList();

        return ResponseEntity.ok(invoices);
    }
}
