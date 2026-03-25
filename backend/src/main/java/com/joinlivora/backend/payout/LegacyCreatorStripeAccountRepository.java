package com.joinlivora.backend.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LegacyCreatorStripeAccountRepository extends JpaRepository<LegacyCreatorStripeAccount, UUID> {
    Optional<LegacyCreatorStripeAccount> findByCreatorId(Long creatorId);
    Optional<LegacyCreatorStripeAccount> findByStripeAccountId(String stripeAccountId);
}
