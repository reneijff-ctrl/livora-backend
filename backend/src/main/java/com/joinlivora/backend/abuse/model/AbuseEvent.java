package com.joinlivora.backend.abuse.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an abuse event for security monitoring and enforcement.
 */
@Entity
@Table(name = "abuse_events", indexes = {
    @Index(name = "idx_abuse_event_user", columnList = "user_id"),
    @Index(name = "idx_abuse_event_ip", columnList = "ip_address"),
    @Index(name = "idx_abuse_event_type", columnList = "event_type"),
    @Index(name = "idx_abuse_event_created", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AbuseEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private AbuseEventType eventType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
