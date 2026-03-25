package com.joinlivora.backend.monetization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tip_actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipAction {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private Long creatorId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private String description;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "sort_order")
    private int sortOrder;
}
