package com.joinlivora.backend.aml.repository;

import com.joinlivora.backend.aml.model.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository("amlRiskScoreRepository")
public interface RiskScoreRepository extends JpaRepository<RiskScore, UUID> {
    Optional<RiskScore> findTopByUserIdOrderByLastEvaluatedAtDesc(UUID userId);
}
