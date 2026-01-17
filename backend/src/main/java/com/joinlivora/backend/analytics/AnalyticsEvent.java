package com.joinlivora.backend.analytics;

import com.joinlivora.backend.user.User;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "analytics_events", indexes = {
    @Index(name = "idx_analytics_event_type", columnList = "event_type"),
    @Index(name = "idx_analytics_user_id", columnList = "user_id"),
    @Index(name = "idx_analytics_created_at", columnList = "created_at"),
    @Index(name = "idx_analytics_funnel_id", columnList = "funnel_id")
})
public class AnalyticsEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private AnalyticsEventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "funnel_id")
    private String funnelId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public AnalyticsEvent() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AnalyticsEventType getEventType() {
        return eventType;
    }

    public void setEventType(AnalyticsEventType eventType) {
        this.eventType = eventType;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFunnelId() {
        return funnelId;
    }

    public void setFunnelId(String funnelId) {
        this.funnelId = funnelId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
