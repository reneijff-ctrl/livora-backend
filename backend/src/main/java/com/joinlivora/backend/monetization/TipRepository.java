package com.joinlivora.backend.monetization;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TipRepository extends JpaRepository<Tip, UUID> {
    Optional<Tip> findByStripePaymentIntentId(String stripePaymentIntentId);
}
