package com.joinlivora.backend.analytics;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_top_content", indexes = {
    @Index(name = "idx_creator_top_content_creator", columnList = "creator_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorTopContent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(nullable = false)
    private String title;

    @Column(name = "total_revenue", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalRevenue;

    @Column(nullable = false)
    private int rank;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
