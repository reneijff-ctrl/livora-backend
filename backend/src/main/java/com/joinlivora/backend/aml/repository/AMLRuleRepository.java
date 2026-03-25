package com.joinlivora.backend.aml.repository;

import com.joinlivora.backend.aml.model.AMLRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository("amlRuleRepository")
public interface AMLRuleRepository extends JpaRepository<AMLRule, UUID> {
    Optional<AMLRule> findByCode(String code);
}
