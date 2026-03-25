package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.RiskExplanation;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RiskExplanationRepository extends JpaRepository<RiskExplanation, UUID> {
    List<RiskExplanation> findAllBySubjectIdAndSubjectTypeOrderByGeneratedAtDesc(UUID subjectId, RiskSubjectType subjectType);
}
