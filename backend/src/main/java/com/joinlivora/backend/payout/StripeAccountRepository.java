package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface StripeAccountRepository extends JpaRepository<StripeAccount, UUID> {
    Optional<StripeAccount> findByUser(User user);
    Optional<StripeAccount> findByStripeAccountId(String stripeAccountId);
}
