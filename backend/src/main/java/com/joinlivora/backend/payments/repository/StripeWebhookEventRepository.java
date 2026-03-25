package com.joinlivora.backend.payments.repository;

import com.joinlivora.backend.payments.model.StripeWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, String> {
    boolean existsByEventId(String eventId);
}
