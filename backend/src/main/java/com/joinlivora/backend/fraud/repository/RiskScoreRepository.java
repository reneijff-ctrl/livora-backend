package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository("fraudRiskScoreEntryRepository")
public interface RiskScoreRepository extends JpaRepository<RiskScore, UUID> {
    Optional<RiskScore> findByUserId(UUID userId);
}
