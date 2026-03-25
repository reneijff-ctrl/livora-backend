package com.joinlivora.backend.abuse.repository;

import com.joinlivora.backend.abuse.model.AbuseReport;
import com.joinlivora.backend.abuse.model.ReportStatus;
import com.joinlivora.backend.abuse.model.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AbuseReportRepository extends JpaRepository<AbuseReport, Long> {

    Page<AbuseReport> findByStatus(ReportStatus status, Pageable pageable);

    Page<AbuseReport> findByTargetUser_Id(Long targetUserId, Pageable pageable);

    Page<AbuseReport> findByTargetStreamId(UUID targetStreamId, Pageable pageable);

    long countByTargetUser_IdAndCreatedAtAfter(Long targetUserId, Instant since);

    long countByTargetStreamIdAndCreatedAtAfter(UUID targetStreamId, Instant since);
}
