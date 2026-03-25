package com.joinlivora.backend.report;

import com.joinlivora.backend.email.EmailService;
import com.joinlivora.backend.livestream.service.LiveStreamService;
import com.joinlivora.backend.report.model.Report;
import com.joinlivora.backend.report.model.ReportReason;
import com.joinlivora.backend.report.model.ReportStatus;
import com.joinlivora.backend.report.repository.ReportRepository;
import com.joinlivora.backend.report.service.ReportService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LiveStreamService LiveStreamService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private ReportService reportService;

    private User reportedUser;
    private User admin;

    @BeforeEach
    void setUp() {
        reportedUser = new User();
        reportedUser.setId(1L);
        reportedUser.setEmail("reported@example.com");
        reportedUser.setStatus(UserStatus.ACTIVE);

        admin = new User();
        admin.setId(2L);
        admin.setEmail("admin@example.com");
        admin.setRole(Role.ADMIN);
    }

    @Test
    void createReport_Underage_ShouldFlagUserStopStreamAndNotifyAdmins() {
        Report report = Report.builder()
                .reportedUserId(1L)
                .reason(ReportReason.UNDERAGE)
                .status(ReportStatus.PENDING)
                .build();

        when(reportRepository.save(any(Report.class))).thenReturn(report);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reportedUser));
        when(userRepository.findAllByRole(eq(Role.ADMIN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(admin)));

        reportService.createReport(report);

        verify(userRepository).save(argThat(user -> user.getStatus() == UserStatus.FLAGGED));
        verify(LiveStreamService).stopLiveStream(1L);
        verify(emailService).sendEmail(eq("admin@example.com"), anyString(), eq("admin-underage-report"), anyMap());
    }

    @Test
    void createReport_OtherReason_ShouldNotFlagOrNotify() {
        Report report = Report.builder()
                .reportedUserId(1L)
                .reason(ReportReason.SPAM)
                .status(ReportStatus.PENDING)
                .build();

        when(reportRepository.save(any(Report.class))).thenReturn(report);

        reportService.createReport(report);

        verify(userRepository, never()).save(any(User.class));
        verify(LiveStreamService, never()).stopLiveStream(anyLong());
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString(), anyMap());
    }
}








