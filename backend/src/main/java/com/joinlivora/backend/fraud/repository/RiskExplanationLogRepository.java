package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.RiskExplanationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RiskExplanationLogRepository extends JpaRepository<RiskExplanationLog, UUID> {
}
