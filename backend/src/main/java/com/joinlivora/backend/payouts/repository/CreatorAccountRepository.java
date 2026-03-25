package com.joinlivora.backend.payouts.repository;

import com.joinlivora.backend.payouts.model.CreatorAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorAccountRepository extends JpaRepository<CreatorAccount, UUID> {
    Optional<CreatorAccount> findByCreatorId(UUID creatorId);
}
