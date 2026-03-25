package com.joinlivora.backend.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "platform_analytics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAnalytics {

    @Id
    private LocalDate date;

    @Builder.Default
    @Column(name = "total_revenue", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "unique_visits", nullable = false)
    private long uniqueVisits = 0;

    @Builder.Default
    @Column(name = "registrations", nullable = false)
    private long registrations = 0;

    @Builder.Default
    @Column(name = "new_subscriptions", nullable = false)
    private long newSubscriptions = 0;

    @Builder.Default
    @Column(name = "active_subscriptions", nullable = false)
    private long activeSubscriptions = 0;

    @Builder.Default
    @Column(name = "churned_subscriptions", nullable = false)
    private long churnedSubscriptions = 0;
}
