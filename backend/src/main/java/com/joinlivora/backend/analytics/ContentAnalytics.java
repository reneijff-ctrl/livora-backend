package com.joinlivora.backend.analytics;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "content_analytics", indexes = {
    @Index(name = "idx_content_analytics_creator_date", columnList = "creator_id, date")
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {"content_id", "date"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAnalytics {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(nullable = false)
    private LocalDate date;

    @Builder.Default
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;
}
