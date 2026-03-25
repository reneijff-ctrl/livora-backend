package com.joinlivora.backend.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorPayoutSettingsRepository extends JpaRepository<CreatorPayoutSettings, UUID> {
    Optional<CreatorPayoutSettings> findByCreatorId(UUID creatorId);
    List<CreatorPayoutSettings> findAllByEnabledTrue();
    List<CreatorPayoutSettings> findAllByStripeAccountId(String stripeAccountId);
}
