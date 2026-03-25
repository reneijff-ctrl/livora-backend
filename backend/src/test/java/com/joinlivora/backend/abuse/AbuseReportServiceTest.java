package com.joinlivora.backend.abuse;

import com.joinlivora.backend.abuse.dto.ReportRequestDTO;
import com.joinlivora.backend.abuse.model.AbuseReport;
import com.joinlivora.backend.abuse.model.ReportReason;
import com.joinlivora.backend.abuse.model.ReportStatus;
import com.joinlivora.backend.abuse.model.ReportTargetType;
import com.joinlivora.backend.abuse.repository.AbuseReportRepository;
import com.joinlivora.backend.fraud.FraudScoringService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbuseReportServiceTest {

    @Mock
    private AbuseReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FraudScoringService fraudScoringService;

    @InjectMocks
    private AbuseReportService reportService;

    private ReportRequestDTO request;

    @BeforeEach
    void setUp() {
        request = new ReportRequestDTO();
        request.setTargetUserId(123L);
        request.setReason(ReportReason.SPAM);
    }

    @Test
    void submitReport_ShouldSaveReport() {
        User reporter = new User();
        reporter.setId(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(reporter);
        
        User targetUser = new User();
        targetUser.setId(123L);
        when(userRepository.getReferenceById(123L)).thenReturn(targetUser);

        when(reportRepository.save(any(AbuseReport.class))).thenAnswer(i -> i.getArguments()[0]);

        AbuseReport result = reportService.submitReport(1L, request);

        assertNotNull(result);
        assertEquals(1L, result.getReporterId());
        assertEquals(123L, result.getTargetUserId());
        assertEquals(ReportStatus.OPEN, result.getStatus());
        verify(reportRepository).save(any(AbuseReport.class));
    }

    @Test
    void submitReport_WhenThresholdReached_ShouldEscalate() {
        User reporter = new User();
        reporter.setId(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(reporter);
        
        User targetUser = new User();
        targetUser.setId(123L);
        targetUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.getReferenceById(123L)).thenReturn(targetUser);

        when(reportRepository.save(any(AbuseReport.class))).thenAnswer(i -> i.getArguments()[0]);
        when(reportRepository.countByTargetUser_IdAndCreatedAtAfter(anyLong(), any())).thenReturn(5L);
        
        when(userRepository.findById(123L)).thenReturn(Optional.of(targetUser));

        reportService.submitReport(1L, request);
        
        java.util.UUID expectedUuid = new java.util.UUID(0L, 123L);

        verify(fraudScoringService).recordDecision(eq(expectedUuid), any(), any(), any());
        verify(userRepository).save(targetUser);
        assertEquals(UserStatus.MANUAL_REVIEW, targetUser.getStatus());
    }

    @Test
    void updateReport_ShouldUpdateStatus() {
        Long reportId = 1L;
        AbuseReport report = new AbuseReport();
        report.setId(reportId);
        report.setStatus(ReportStatus.OPEN);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(AbuseReport.class))).thenAnswer(i -> i.getArguments()[0]);

        com.joinlivora.backend.abuse.dto.ReportUpdateDTO update = new com.joinlivora.backend.abuse.dto.ReportUpdateDTO();
        update.setStatus(ReportStatus.RESOLVED);

        AbuseReport result = reportService.updateReport(reportId, update);

        assertEquals(ReportStatus.RESOLVED, result.getStatus());
        verify(reportRepository).save(report);
    }
}








