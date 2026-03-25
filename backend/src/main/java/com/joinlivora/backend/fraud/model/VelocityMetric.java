package com.joinlivora.backend.fraud.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "velocity_metrics", indexes = {
    @Index(name = "idx_velocity_metrics_user_id", columnList = "user_id"),
    @Index(name = "idx_velocity_metrics_action_type", columnList = "action_type"),
    @Index(name = "idx_velocity_metrics_window_end", columnList = "window_end")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_velocity_metrics_user_action_window", columnNames = {"user_id", "action_type", "window_start", "window_end"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityMetric {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false)
    private VelocityActionType actionType;

    @Column(nullable = false)
    private int count;

    @Column(name = "window_start", nullable = false, updatable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false, updatable = false)
    private Instant windowEnd;
}
