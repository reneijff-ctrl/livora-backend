package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.SubscriptionAdminDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubscriptionController {

    private final UserSubscriptionRepository subscriptionRepository;

    @GetMapping
    public Page<SubscriptionAdminDTO> getSubscriptions(Pageable pageable) {
        Page<UserSubscription> page = subscriptionRepository.findAllWithUser(pageable);

        return page.map(sub -> new SubscriptionAdminDTO(
                sub.getId(),
                sub.getUser().getEmail(),
                sub.getStatus().name(),
                sub.getCreatedAt()
        ));
    }
}
