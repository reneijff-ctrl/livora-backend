package com.joinlivora.backend.monetization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tip_goals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipGoal {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private Long creatorId;

    private String title;

    @Column(nullable = false)
    private Long targetAmount;

    @Column(nullable = false)
    private Long currentAmount;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder.Default
    private boolean autoReset = false;

    private Integer orderIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private TipGoalGroup group;

    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.currentAmount == null) {
            this.currentAmount = 0L;
        }
    }
}
