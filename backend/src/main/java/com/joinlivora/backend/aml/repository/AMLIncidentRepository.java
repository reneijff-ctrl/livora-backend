package com.joinlivora.backend.aml.repository;

import com.joinlivora.backend.aml.model.AMLIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository("amlIncidentRepository")
public interface AMLIncidentRepository extends JpaRepository<AMLIncident, UUID> {
    List<AMLIncident> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
