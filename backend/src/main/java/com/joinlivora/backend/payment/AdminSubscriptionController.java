package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.AdminSubscriptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubscriptionController {

    private final UserSubscriptionRepository subscriptionRepository;

    @GetMapping
    public List<AdminSubscriptionResponse> getAllSubscriptions() {
        return subscriptionRepository.findAll().stream()
                .map(sub -> AdminSubscriptionResponse.builder()
                        .id(sub.getId())
                        .userEmail(sub.getUser().getEmail())
                        .status(sub.getStatus())
                        .stripeSubscriptionId(sub.getStripeSubscriptionId())
                        .createdAt(sub.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
