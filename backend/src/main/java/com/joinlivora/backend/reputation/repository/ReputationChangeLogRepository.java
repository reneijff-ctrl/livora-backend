package com.joinlivora.backend.reputation.repository;

import com.joinlivora.backend.reputation.model.ReputationChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReputationChangeLogRepository extends JpaRepository<ReputationChangeLog, UUID> {
}
