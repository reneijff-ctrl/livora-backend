package com.joinlivora.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@lombok.RequiredArgsConstructor
public class AdminController {

    private final com.joinlivora.backend.payout.CreatorEarningRepository creatorEarningRepository;
    private final com.joinlivora.backend.payment.PaymentRepository paymentRepository;
    private final com.joinlivora.backend.payment.UserSubscriptionRepository subscriptionRepository;
    private final com.joinlivora.backend.monetization.TipRepository tipRepository;
    private final com.joinlivora.backend.monetization.PpvPurchaseRepository ppvPurchaseRepository;

    @GetMapping("/dashboard")
    public String dashboard() {
        return "🛠️ Admin dashboard";
    }

    @GetMapping("/tips")
    public java.util.List<com.joinlivora.backend.monetization.Tip> getAllTips() {
        return tipRepository.findAll();
    }

    @GetMapping("/ppv-sales")
    public java.util.List<com.joinlivora.backend.monetization.PpvPurchase> getAllPpvSales() {
        return ppvPurchaseRepository.findAll();
    }

    @GetMapping("/stats")
    public java.util.Map<String, Object> getStats() {
        java.math.BigDecimal totalRevenue = paymentRepository.calculateRevenue(java.time.Instant.EPOCH);
        if (totalRevenue == null) totalRevenue = java.math.BigDecimal.ZERO;
        
        long totalPayments = paymentRepository.count();
        long activeSubscriptions = subscriptionRepository.countActiveSubscriptions();
        
        return java.util.Map.of(
            "totalPlatformRevenue", totalRevenue,
            "totalPaymentsCount", totalPayments,
            "activeSubscriptions", activeSubscriptions
        );
    }
}
