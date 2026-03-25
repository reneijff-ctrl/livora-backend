package com.joinlivora.backend.report.repository;

import com.joinlivora.backend.report.model.Report;
import com.joinlivora.backend.report.model.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {
    List<Report> findByStatus(ReportStatus status);
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);
    long countByStatus(ReportStatus status);
    List<Report> findByReportedUserId(Long reportedUserId);
    List<Report> findByReporterUserId(Long reporterUserId);
}
