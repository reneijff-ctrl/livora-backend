package com.joinlivora.backend.audit.export;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "export_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type; // e.g. PAYOUT_AUDIT

    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

    private String filePath;

    private Long requestedByAdminId;

    private Instant createdAt;

    private Instant completedAt;

    private String errorMessage;
}
