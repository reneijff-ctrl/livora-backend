package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.FraudRiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FraudRiskScoreRepository extends JpaRepository<FraudRiskScore, Long> {
}
