package com.joinlivora.backend.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_stats")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorStats {

    @Id
    @Column(name = "creator_id")
    private UUID creatorId;

    @Builder.Default
    @Column(name = "total_net_earnings", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalNetEarnings = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_net_tokens", nullable = false)
    private long totalNetTokens = 0;

    @Builder.Default
    @Column(name = "today_net_earnings", nullable = false, precision = 19, scale = 2)
    private BigDecimal todayNetEarnings = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "today_net_tokens", nullable = false)
    private long todayNetTokens = 0;

    @Builder.Default
    @Column(name = "subscription_count", nullable = false)
    private long subscriptionCount = 0;

    @Builder.Default
    @Column(name = "tips_count", nullable = false)
    private long tipsCount = 0;

    @Builder.Default
    @Column(name = "highlights_count", nullable = false)
    private long highlightsCount = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
