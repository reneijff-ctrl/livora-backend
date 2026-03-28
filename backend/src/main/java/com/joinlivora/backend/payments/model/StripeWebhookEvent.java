package com.joinlivora.backend.payments.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "stripe_webhook_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeWebhookEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "type", nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "payload_hash")
    private String payloadHash;

    public enum Status {
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
