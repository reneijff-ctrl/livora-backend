package com.joinlivora.backend.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ContentAnalyticsRepository extends JpaRepository<ContentAnalytics, UUID> {
    List<ContentAnalytics> findAllByCreatorIdAndDateBetween(UUID creatorId, LocalDate start, LocalDate end);
}
