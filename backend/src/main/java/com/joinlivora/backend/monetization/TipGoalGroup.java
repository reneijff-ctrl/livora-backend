package com.joinlivora.backend.monetization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tip_goal_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipGoalGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private Long creatorId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private Long targetAmount;

    @Column(nullable = false)
    @Builder.Default
    private Long currentAmount = 0L;

    @Builder.Default
    @Column(name = "is_active")
    private boolean active = false;

    @Builder.Default
    private boolean autoReset = false;

    @Builder.Default
    private Integer orderIndex = 0;

    private Instant createdAt;

    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
    @OrderBy("targetAmount ASC")
    @Builder.Default
    private List<TipGoal> milestones = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.currentAmount == null) {
            this.currentAmount = 0L;
        }
    }
}
