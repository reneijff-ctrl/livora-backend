package com.joinlivora.backend.fraud.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_fingerprints", indexes = {
    @Index(name = "idx_device_fingerprints_user_id", columnList = "user_id"),
    @Index(name = "idx_device_fingerprints_hash", columnList = "fingerprint_hash"),
    @Index(name = "idx_device_fingerprints_ip_address", columnList = "ip_address")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_device_fingerprints_user_hash", columnNames = {"user_id", "fingerprint_hash"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceFingerprint {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "fingerprint_hash", nullable = false)
    private String fingerprintHash;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "first_seen", nullable = false, updatable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Builder.Default
    @Column(nullable = false)
    private boolean trusted = false;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (firstSeen == null) {
            firstSeen = now;
        }
        if (lastSeen == null) {
            lastSeen = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastSeen = Instant.now();
    }
}
