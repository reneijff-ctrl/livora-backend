package com.joinlivora.backend.report.service;

import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.report.model.Report;
import com.joinlivora.backend.report.model.ReportReason;
import com.joinlivora.backend.report.model.ReportStatus;
import com.joinlivora.backend.report.repository.ReportRepository;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.livestream.service.LiveStreamService;
import com.joinlivora.backend.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final LiveStreamService liveStreamService;
    private final EmailService emailService;
    private final AdminRealtimeEventService adminRealtimeEventService;

    @Transactional
    public Report createReport(Report report) {
        log.info("Creating new report against user {}. Reason: {}", report.getReportedUserId(), report.getReason());
        if (report.getStatus() == null) {
            report.setStatus(ReportStatus.PENDING);
        }
        Report saved = reportRepository.save(report);

        if (report.getReason() == ReportReason.UNDERAGE) {
            handleUnderageReport(saved);
        }

        adminRealtimeEventService.broadcastReportCreated(saved);

        return saved;
    }

    private void handleUnderageReport(Report report) {
        log.warn("UNDERAGE report received for user {}. Taking immediate action.", report.getReportedUserId());

        // 1. Flag reported user
        userRepository.findById(report.getReportedUserId()).ifPresent(user -> {
            user.setStatus(UserStatus.FLAGGED);
            userRepository.save(user);
            log.info("User {} status set to FLAGGED due to UNDERAGE report", user.getId());
        });

        // 2. Disable active streams
        try {
            liveStreamService.stopLiveStream(report.getReportedUserId());
            log.info("Active stream for user {} stopped due to UNDERAGE report", report.getReportedUserId());
        } catch (ResourceNotFoundException e) {
            log.debug("No active stream to stop for user {}", report.getReportedUserId());
        }

        // 3. Notify admins via email
        List<User> admins = userRepository.findAllByRole(Role.ADMIN, Pageable.unpaged()).getContent();
        for (User admin : admins) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("report", report);
            variables.put("admin", admin);
            emailService.sendEmail(admin.getEmail(), "URGENT: Underage Content Report", "admin-underage-report", variables);
        }
    }

    public Report getReport(UUID id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));
    }

    public List<Report> getAllReports() {
        return reportRepository.findAll();
    }

    public Page<Report> getAllReports(ReportStatus status, Pageable pageable) {
        if (status != null) {
            return reportRepository.findByStatus(status, pageable);
        }
        return reportRepository.findAll(pageable);
    }

    public List<Report> getReportsByStatus(ReportStatus status) {
        return reportRepository.findByStatus(status);
    }

    @Transactional
    public Report updateReportStatus(UUID id, ReportStatus status) {
        log.info("Updating report {} status to {}", id, status);
        Report report = getReport(id);
        report.setStatus(status);
        return reportRepository.save(report);
    }

    public List<Report> getReportsAgainstUser(Long userId) {
        return reportRepository.findByReportedUserId(userId);
    }

    public List<Report> getReportsByReporter(Long userId) {
        return reportRepository.findByReporterUserId(userId);
    }
}
