package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.FraudDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FraudDecisionRepository extends JpaRepository<FraudDecision, UUID> {
    Optional<FraudDecision> findFirstByRelatedTipIdOrderByCreatedAtDesc(Long relatedTipId);
    Optional<FraudDecision> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
}
