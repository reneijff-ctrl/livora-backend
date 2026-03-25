package com.joinlivora.backend.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorPayoutStateRepository extends JpaRepository<CreatorPayoutState, UUID> {
    Optional<CreatorPayoutState> findByCreatorId(UUID creatorId);
}
