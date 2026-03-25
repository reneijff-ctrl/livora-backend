package com.joinlivora.backend.presence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "creator_presence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatorPresence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", unique = true, nullable = false)
    private Long creatorId; // Standardized: ID from Creator entity (creator_records table)

    @Column(nullable = false)
    @Builder.Default
    private boolean online = false;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;
}
