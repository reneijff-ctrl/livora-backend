package com.joinlivora.backend.monetization;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "weekly_tip_leaderboards", uniqueConstraints = {
    @UniqueConstraint(name = "uk_weekly_tip_leaderboard", columnNames = {"creator_id", "username", "week_number", "year"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyTipLeaderboard {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "year", nullable = false)
    private Integer year;
}
