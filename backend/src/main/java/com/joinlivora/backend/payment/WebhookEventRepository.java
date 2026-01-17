package com.joinlivora.backend.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    boolean existsByStripeEventId(String stripeEventId);
}
