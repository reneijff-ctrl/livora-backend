package com.joinlivora.backend.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface PlatformAnalyticsRepository extends JpaRepository<PlatformAnalytics, LocalDate> {

    @org.springframework.data.jpa.repository.Query("SELECT SUM(p.totalRevenue) FROM PlatformAnalytics p WHERE p.date >= :since")
    java.math.BigDecimal sumRevenueSince(@org.springframework.data.repository.query.Param("since") java.time.LocalDate since);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(p.uniqueVisits) FROM PlatformAnalytics p WHERE p.date >= :since")
    Long sumUniqueVisitsSince(@org.springframework.data.repository.query.Param("since") java.time.LocalDate since);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(p.registrations) FROM PlatformAnalytics p WHERE p.date >= :since")
    Long sumRegistrationsSince(@org.springframework.data.repository.query.Param("since") java.time.LocalDate since);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(p.newSubscriptions) FROM PlatformAnalytics p WHERE p.date >= :since")
    Long sumNewSubscriptionsSince(@org.springframework.data.repository.query.Param("since") java.time.LocalDate since);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(p.churnedSubscriptions) FROM PlatformAnalytics p WHERE p.date >= :since")
    Long sumChurnedSubscriptionsSince(@org.springframework.data.repository.query.Param("since") java.time.LocalDate since);

    java.util.Optional<PlatformAnalytics> findFirstByOrderByDateDesc();
}
