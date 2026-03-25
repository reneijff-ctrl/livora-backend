package com.joinlivora.backend.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperimentAnalyticsRepository extends JpaRepository<ExperimentAnalytics, ExperimentAnalyticsId> {
    List<ExperimentAnalytics> findAllByExperimentKey(String experimentKey);
}
