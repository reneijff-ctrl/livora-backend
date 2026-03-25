package com.joinlivora.backend.analytics;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "experiment_analytics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(ExperimentAnalyticsId.class)
public class ExperimentAnalytics {

    @Id
    @Column(name = "experiment_key")
    private String experimentKey;

    @Id
    private String variant;

    @Builder.Default
    @Column(nullable = false)
    private long count = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
