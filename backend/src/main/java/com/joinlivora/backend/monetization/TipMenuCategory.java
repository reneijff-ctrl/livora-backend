package com.joinlivora.backend.monetization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tip_menu_categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipMenuCategory {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "is_enabled")
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
