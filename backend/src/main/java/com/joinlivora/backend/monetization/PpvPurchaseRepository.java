package com.joinlivora.backend.monetization;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PpvPurchaseRepository extends JpaRepository<PpvPurchase, UUID> {
    Optional<PpvPurchase> findByPpvContentAndUserAndStatus(PpvContent ppvContent, User user, PpvPurchaseStatus status);
    Optional<PpvPurchase> findByStripePaymentIntentId(String stripePaymentIntentId);
}
