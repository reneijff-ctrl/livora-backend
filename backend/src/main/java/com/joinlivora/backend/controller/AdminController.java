package com.joinlivora.backend.controller;

import com.joinlivora.backend.admin.dto.PpvPurchaseDto;
import com.joinlivora.backend.admin.dto.TipDto;
import com.joinlivora.backend.admin.dto.UserAdminResponseDTO;
import com.joinlivora.backend.admin.dto.UserFilterRequestDTO;
import com.joinlivora.backend.admin.service.AdminService;
import com.joinlivora.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final com.joinlivora.backend.payout.CreatorEarningRepository creatorEarningRepository;
    private final com.joinlivora.backend.payment.PaymentRepository paymentRepository;
    private final com.joinlivora.backend.payment.UserSubscriptionRepository subscriptionRepository;
    private final com.joinlivora.backend.monetization.TipRepository tipRepository;
    private final com.joinlivora.backend.monetization.PpvPurchaseRepository ppvPurchaseRepository;
    private final AdminService adminService;

    @GetMapping("/dashboard")
    public String dashboard() {
        return "🛠️ Admin dashboard";
    }

    @GetMapping("/users")
    public Page<UserAdminResponseDTO> getUsers(UserFilterRequestDTO filter, Pageable pageable) {
        return adminService.getUsers(filter, pageable);
    }

    @PostMapping("/users/{userId}/status")
    public void updateUserStatus(@PathVariable Long userId, @RequestParam UserStatus status) {
        adminService.updateUserStatus(userId, status);
    }

    @PostMapping("/users/{userId}/shadowban")
    public void shadowbanUser(@PathVariable Long userId, @RequestParam boolean shadowbanned) {
        adminService.shadowbanUser(userId, shadowbanned);
    }

    @PostMapping("/users/{userId}/payouts")
    public void togglePayouts(@PathVariable Long userId, @RequestParam boolean enabled) {
        adminService.togglePayouts(userId, enabled);
    }

    @PostMapping("/users/{userId}/logout")
    public void forceLogout(@PathVariable Long userId) {
        adminService.forceLogout(userId);
    }

    @GetMapping("/tips")
    public List<TipDto> getAllTips() {
        return tipRepository.findAllWithUsers().stream()
                .map(tip -> TipDto.builder()
                        .id(tip.getId())
                        .senderId(tip.getSenderUserId().getId())
                        .senderUsername(tip.getSenderUserId().getEmail()) // Fallback to email for admin if username missing
                        .creatorId(tip.getCreatorUserId().getId())
                        .creatorUsername(tip.getCreatorUserId().getEmail())
                        .amount(tip.getAmount())
                        .currency(tip.getCurrency())
                        .message(tip.getMessage())
                        .status(tip.getStatus())
                        .createdAt(tip.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @GetMapping("/ppv-sales")
    public List<PpvPurchaseDto> getAllPpvSales() {
        return ppvPurchaseRepository.findAllWithDetails().stream()
                .map(p -> PpvPurchaseDto.builder()
                        .id(p.getId())
                        .userId(p.getUser().getId())
                        .username(p.getUser().getEmail())
                        .ppvContentId(p.getPpvContent().getId())
                        .ppvContentTitle(p.getPpvContent().getTitle())
                        .amount(p.getAmount())
                        .status(p.getStatus())
                        .purchasedAt(p.getPurchasedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        java.math.BigDecimal totalRevenue = paymentRepository.calculateRevenue(java.time.Instant.EPOCH);
        if (totalRevenue == null) totalRevenue = java.math.BigDecimal.ZERO;
        
        long totalPayments = paymentRepository.count();
        long activeSubscriptions = subscriptionRepository.countActiveSubscriptions();
        
        return Map.of(
            "totalPlatformRevenue", totalRevenue,
            "totalPaymentsCount", totalPayments,
            "activeSubscriptions", activeSubscriptions
        );
    }
}
