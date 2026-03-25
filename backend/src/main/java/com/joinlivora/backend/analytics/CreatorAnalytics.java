package com.joinlivora.backend.analytics;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "creator_analytics", indexes = {
    @Index(name = "idx_creator_analytics_creator_date", columnList = "creator_id, date", unique = true),
    @Index(name = "idx_creator_analytics_date", columnList = "date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorAnalytics {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(nullable = false)
    private LocalDate date;

    @Builder.Default
    @Column(name = "total_views", nullable = false)
    private long totalViews = 0;

    @Builder.Default
    @Column(name = "unique_viewers", nullable = false)
    private long uniqueViewers = 0;

    @Builder.Default
    @Column(name = "total_earnings", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalEarnings = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "subscription_earnings", nullable = false, precision = 19, scale = 2)
    private BigDecimal subscriptionEarnings = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "ppv_earnings", nullable = false, precision = 19, scale = 2)
    private BigDecimal ppvEarnings = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "tips_earnings", nullable = false, precision = 19, scale = 2)
    private BigDecimal tipsEarnings = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "live_stream_earnings", nullable = false, precision = 19, scale = 2)
    private BigDecimal liveStreamEarnings = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "subscriptions_count", nullable = false)
    private long subscriptionsCount = 0;

    @Builder.Default
    @Column(name = "returning_viewers", nullable = false)
    private long returningViewers = 0;

    @Builder.Default
    @Column(name = "avg_session_duration", nullable = false)
    private long avgSessionDuration = 0;

    @Builder.Default
    @Column(name = "messages_per_viewer", nullable = false)
    private double messagesPerViewer = 0.0;
}
