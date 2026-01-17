package com.joinlivora.backend.payment;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    Optional<UserSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    Optional<UserSubscription> findByUserAndStatus(User user, SubscriptionStatus status);
    Optional<UserSubscription> findFirstByUserOrderByCreatedAtDesc(User user);

    @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.status = 'ACTIVE'")
    long countActiveSubscriptions();

    @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.status = 'CANCELED' AND s.updatedAt >= :since")
    long countCanceledSubscriptions(@org.springframework.data.repository.query.Param("since") java.time.Instant since);
}
