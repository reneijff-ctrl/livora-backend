package com.joinlivora.backend.monetization;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_collusion_records", indexes = {
    @Index(name = "idx_collusion_record_creator", columnList = "creator_id"),
    @Index(name = "idx_collusion_record_evaluated_at", columnList = "evaluated_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorCollusionRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "detected_pattern", columnDefinition = "TEXT")
    private String detectedPattern;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @PrePersist
    protected void onCreate() {
        if (evaluatedAt == null) {
            evaluatedAt = Instant.now();
        }
    }
}
