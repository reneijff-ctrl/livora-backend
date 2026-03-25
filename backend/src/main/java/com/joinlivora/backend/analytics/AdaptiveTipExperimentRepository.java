package com.joinlivora.backend.analytics;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AdaptiveTipExperimentRepository extends JpaRepository<AdaptiveTipExperiment, UUID> {
    List<AdaptiveTipExperiment> findByCreatorAndCreatedAtAfter(User creator, LocalDateTime date);
    List<AdaptiveTipExperiment> findByEvaluatedAtIsNull();
    List<AdaptiveTipExperiment> findAllByCreatorOrderByCreatedAtDesc(User creator);
}
