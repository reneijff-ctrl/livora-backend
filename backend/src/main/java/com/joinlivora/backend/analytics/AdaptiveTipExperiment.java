package com.joinlivora.backend.analytics;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "adaptive_tip_experiments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveTipExperiment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    private double suggestedFloor;
    private double previousFloor;
    private int riskScore;
    private double momentum;
    private String confidenceTier;
    private String experimentGroup;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime evaluatedAt;
    
    private Double baselineRevenue;
    private Double revenueAfter24h;
    private Double revenueAfter7d;
    private Double revenueLift;
    private Double riskDelta;
    private Integer newRiskScore;
    private Boolean success;
}
