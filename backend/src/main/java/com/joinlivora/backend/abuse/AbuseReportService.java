package com.joinlivora.backend.abuse;

import com.joinlivora.backend.abuse.dto.ReportRequestDTO;
import com.joinlivora.backend.abuse.dto.ReportUpdateDTO;
import com.joinlivora.backend.abuse.model.AbuseReport;
import com.joinlivora.backend.abuse.model.ReportStatus;
import com.joinlivora.backend.abuse.repository.AbuseReportRepository;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AbuseReportService {

    private final AbuseReportRepository reportRepository;
    private final UserRepository userRepository;
    private final com.joinlivora.backend.fraud.FraudScoringService fraudScoringService;

    @Transactional
    public AbuseReport submitReport(Long reporterId, ReportRequestDTO request) {
        log.info("User {} reporting targetUser: {}, targetStream: {}. Reason: {}", 
                reporterId, request.getTargetUserId(), request.getTargetStreamId(), request.getReason());

        AbuseReport report = AbuseReport.builder()
                .reporter(userRepository.getReferenceById(reporterId))
                .targetUser(request.getTargetUserId() != null ? userRepository.getReferenceById(request.getTargetUserId()) : null)
                .targetStreamId(request.getTargetStreamId())
                .reason(request.getReason())
                .status(ReportStatus.OPEN)
                .build();

        AbuseReport saved = reportRepository.save(report);
        
        checkAutoEscalation(saved);
        
        return saved;
    }

    public Page<AbuseReport> getReports(ReportStatus status, Pageable pageable) {
        if (status != null) {
            return reportRepository.findByStatus(status, pageable);
        }
        return reportRepository.findAll(pageable);
    }

    @Transactional
    public AbuseReport updateReport(Long reportId, ReportUpdateDTO update) {
        AbuseReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Abuse report not found"));

        if (update.getStatus() != null) {
            report.setStatus(update.getStatus());
        }

        return reportRepository.save(report);
    }

    private void checkAutoEscalation(AbuseReport report) {
        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);
        
        if (report.getTargetUserId() != null) {
            long count = reportRepository.countByTargetUser_IdAndCreatedAtAfter(report.getTargetUserId(), since24h);
            log.debug("Auto-escalation check: Target user {} has {} reports in last 24h", report.getTargetUserId(), count);
            if (count >= 5) {
                log.warn("Auto-escalating user {}: {} reports in 24h", report.getTargetUserId(), count);
                escalateUser(report.getTargetUserId(), count);
            }
        }
        
        if (report.getTargetStreamId() != null) {
            long count = reportRepository.countByTargetStreamIdAndCreatedAtAfter(report.getTargetStreamId(), since24h);
            log.debug("Auto-escalation check: Target stream {} has {} reports in last 24h", report.getTargetStreamId(), count);
            if (count >= 10) {
                 log.warn("Auto-escalating stream {}: {} reports in 24h", report.getTargetStreamId(), count);
            }
        }
    }

    private void escalateUser(Long userId, long reportCount) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getStatus() != UserStatus.SUSPENDED && user.getStatus() != UserStatus.TERMINATED) {
            com.joinlivora.backend.fraud.model.FraudRiskResult result = new com.joinlivora.backend.fraud.model.FraudRiskResult(
                    com.joinlivora.backend.fraud.model.FraudRiskLevel.MEDIUM,
                    50,
                    java.util.List.of("Auto-escalated due to " + reportCount + " abuse reports")
            );
            fraudScoringService.recordDecision(new UUID(0L, userId), null, null, result);
            
            if (user.getStatus() == UserStatus.ACTIVE) {
                user.setStatus(UserStatus.MANUAL_REVIEW);
                userRepository.save(user);
            }
        }
    }
}
