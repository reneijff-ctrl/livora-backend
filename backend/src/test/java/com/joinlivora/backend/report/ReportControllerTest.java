package com.joinlivora.backend.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.report.dto.ReportRequest;
import com.joinlivora.backend.report.model.Report;
import com.joinlivora.backend.report.model.ReportReason;
import com.joinlivora.backend.report.model.ReportStatus;
import com.joinlivora.backend.report.service.ReportService;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import(com.joinlivora.backend.config.SecurityConfig.class)
@org.springframework.data.web.config.EnableSpringDataWebSupport
public class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReportService reportService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private com.joinlivora.backend.analytics.FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;

    @MockBean
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;

    @MockBean
    private com.joinlivora.backend.fraud.repository.UserRiskStateRepository userRiskStateRepository;

    @BeforeEach
    void setup() throws jakarta.servlet.ServletException, java.io.IOException {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rateLimitingFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(funnelTrackingFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "reporter@example.com")
    void createReport_ShouldReturnCreated() throws Exception {
        User reporter = new User();
        reporter.setId(1L);
        reporter.setEmail("reporter@example.com");
        when(userRepository.findByEmail("reporter@example.com")).thenReturn(Optional.of(reporter));

        ReportRequest request = ReportRequest.builder()
                .reportedUserId(123L)
                .streamId(UUID.randomUUID())
                .reason(ReportReason.HARASSMENT)
                .description("Offensive behavior")
                .build();

        Report report = Report.builder()
                .id(UUID.randomUUID())
                .reporterUserId(1L)
                .reportedUserId(123L)
                .streamId(request.getStreamId())
                .reason(ReportReason.HARASSMENT)
                .description("Offensive behavior")
                .status(ReportStatus.PENDING)
                .build();

        when(reportService.createReport(any(Report.class))).thenReturn(report);

        mockMvc.perform(post("/api/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reporterUserId").value(1))
                .andExpect(jsonPath("$.reportedUserId").value(123))
                .andExpect(jsonPath("$.reason").value("HARASSMENT"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createReport_WithoutAuth_ShouldReturnUnauthorized() throws Exception {
        ReportRequest request = new ReportRequest();
        mockMvc.perform(post("/api/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "reporter@example.com")
    void createReport_WithInvalidInput_ShouldReturnBadRequest() throws Exception {
        ReportRequest request = new ReportRequest(); // Missing reportedUserId and reason

        mockMvc.perform(post("/api/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void getAllReports_AsAdmin_ShouldReturnOk() throws Exception {
        Report report = Report.builder()
                .id(UUID.randomUUID())
                .reportedUserId(1L)
                .reason(ReportReason.SPAM)
                .status(ReportStatus.PENDING)
                .build();

        Page<Report> page = new PageImpl<>(List.of(report), PageRequest.of(0, 20), 1);
        when(reportService.getAllReports(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/reports")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reason").value("SPAM"));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void getAllReports_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/reports")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void updateReportStatus_AsAdmin_ShouldReturnOk() throws Exception {
        UUID reportId = UUID.randomUUID();
        String updateJson = "{\"status\": \"RESOLVED\"}";

        Report report = Report.builder()
                .id(reportId)
                .status(ReportStatus.RESOLVED)
                .build();
        when(reportService.updateReportStatus(any(), any())).thenReturn(report);

        mockMvc.perform(patch("/api/admin/reports/" + reportId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void updateReportStatus_WithInvalidStatus_ShouldReturnBadRequest() throws Exception {
        UUID reportId = UUID.randomUUID();
        String updateJson = "{\"status\": null}";

        mockMvc.perform(patch("/api/admin/reports/" + reportId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void updateReportStatus_AsUser_ShouldReturnForbidden() throws Exception {
        UUID reportId = UUID.randomUUID();
        String updateJson = "{\"status\": \"RESOLVED\"}";

        mockMvc.perform(patch("/api/admin/reports/" + reportId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isForbidden());
    }
}








