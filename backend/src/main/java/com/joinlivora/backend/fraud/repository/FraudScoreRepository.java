package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.FraudScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FraudScoreRepository extends JpaRepository<FraudScore, Long> {
    Optional<FraudScore> findByUserId(Long userId);
    List<FraudScore> findAllByRiskLevelNot(String riskLevel);
}
