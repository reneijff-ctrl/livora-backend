package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.FraudRiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FraudRiskAssessmentRepository extends JpaRepository<FraudRiskAssessment, UUID> {
    List<FraudRiskAssessment> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
