package com.joinlivora.backend.creator.repository;

import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.CreatorStripeAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CreatorStripeAccountRepository extends JpaRepository<CreatorStripeAccount, Long> {
    Optional<CreatorStripeAccount> findByCreator(CreatorProfile creator);
    Optional<CreatorStripeAccount> findByCreatorId(Long creatorId);
    Optional<CreatorStripeAccount> findByStripeAccountId(String stripeAccountId);
}
