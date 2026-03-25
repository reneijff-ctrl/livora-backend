package com.joinlivora.backend.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorAnalyticsRepository extends JpaRepository<CreatorAnalytics, UUID> {
    Optional<CreatorAnalytics> findByCreatorIdAndDate(UUID creatorId, LocalDate date);
    List<CreatorAnalytics> findAllByCreatorIdAndDateBetweenOrderByDateAsc(UUID creatorId, LocalDate start, LocalDate end);
    List<CreatorAnalytics> findAllByDateBetween(LocalDate start, LocalDate end);
}
