package com.joinlivora.backend.abuse.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "abuse_reports", indexes = {
    @Index(name = "idx_abuse_report_reporter_id", columnList = "reporter_id"),
    @Index(name = "idx_abuse_report_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbuseReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    @JsonIgnore
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    @JsonIgnore
    private User targetUser;

    @Column(name = "target_stream_id")
    private UUID targetStreamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = ReportStatus.OPEN;
        }
    }

    public Long getReporterId() {
        return reporter != null ? reporter.getId() : null;
    }

    public Long getTargetUserId() {
        return targetUser != null ? targetUser.getId() : null;
    }
}
