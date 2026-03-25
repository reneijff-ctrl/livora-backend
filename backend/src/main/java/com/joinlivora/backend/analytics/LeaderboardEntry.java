package com.joinlivora.backend.analytics;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leaderboard_entries", indexes = {
    @Index(name = "idx_leaderboard_period_rank", columnList = "period, leaderboard_rank"),
    @Index(name = "idx_leaderboard_period_creator", columnList = "period, creator_id"),
    @Index(name = "idx_leaderboard_period_date", columnList = "period, reference_date"),
    @Index(name = "idx_leaderboard_category", columnList = "category"),
    @Index(name = "idx_leaderboard_unique_entry", columnList = "period, creator_id, reference_date, category", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaderboardPeriod period;

    @Column(name = "leaderboard_rank", nullable = false)
    private int rank;

    @Column
    private String category;

    @Builder.Default
    @Column(name = "total_earnings", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalEarnings = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_viewers", nullable = false)
    private long totalViewers = 0;

    @Builder.Default
    @Column(name = "total_subscribers", nullable = false)
    private long totalSubscribers = 0;

    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;
}
