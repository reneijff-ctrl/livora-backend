package com.joinlivora.backend.featureflag;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feature_flags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlag {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "flag_key", unique = true, nullable = false)
    private String key;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;

    @Builder.Default
    @Column(name = "rollout_percentage", nullable = false)
    private int rolloutPercentage = 0; // 0-100

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureEnvironment environment = FeatureEnvironment.PROD;

    @Builder.Default
    @Column(name = "is_experiment", nullable = false)
    private boolean experiment = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
