package com.joinlivora.backend.admin.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminDashboardService adminDashboardService;

    @InjectMocks
    private AdminDashboardController adminDashboardController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminDashboardController).build();
    }

    @Test
    void getDashboardData_ShouldReturnAllData() throws Exception {
        AdminDashboardMetrics metrics = AdminDashboardMetrics.builder()
                .totalCreators(10L)
                .build();
        AdminDashboardChartsDTO chartsDTO = AdminDashboardChartsDTO.builder()
                .dailyUsersLast7Days(List.of(new ChartPoint("2026-03-01", BigDecimal.TEN)))
                .build();
        AdminDashboardResponse response = AdminDashboardResponse.builder()
                .metrics(metrics)
                .charts(chartsDTO)
                .build();

        when(adminDashboardService.getDashboardData()).thenReturn(response);

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.totalCreators").value(10))
                .andExpect(jsonPath("$.charts.dailyUsersLast7Days[0].label").value("2026-03-01"));
    }

    @Test
    void getMetrics_ShouldReturnMetrics() throws Exception {
        AdminDashboardMetrics metrics = AdminDashboardMetrics.builder()
                .totalCreators(10L)
                .verifiedCreators(5L)
                .activeFreezes(2L)
                .pendingPayouts(3L)
                .pendingApplications(1L)
                .pendingAmount(new BigDecimal("150.00"))
                .auditEvents24h(20L)
                .onlineUsers(50L)
                .websocketSessions(40L)
                .activeStreams(5L)
                .newUsersToday(100L)
                .todayRevenue(new BigDecimal("1000.00"))
                .openReports(5L)
                .build();
        when(adminDashboardService.getMetrics()).thenReturn(metrics);

        mockMvc.perform(get("/api/admin/dashboard/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCreators").value(10))
                .andExpect(jsonPath("$.verifiedCreators").value(5))
                .andExpect(jsonPath("$.activeFreezes").value(2))
                .andExpect(jsonPath("$.pendingPayouts").value(3))
                .andExpect(jsonPath("$.pendingApplications").value(1))
                .andExpect(jsonPath("$.pendingAmount").value(150.00))
                .andExpect(jsonPath("$.auditEvents24h").value(20))
                .andExpect(jsonPath("$.onlineUsers").value(50))
                .andExpect(jsonPath("$.websocketSessions").value(40))
                .andExpect(jsonPath("$.activeStreams").value(5))
                .andExpect(jsonPath("$.newUsersToday").value(100))
                .andExpect(jsonPath("$.todayRevenue").value(1000.00))
                .andExpect(jsonPath("$.openReports").value(5));
    }

    @Test
    void getCharts_ShouldReturnCharts() throws Exception {
        AdminDashboardChartsDTO chartsDTO = AdminDashboardChartsDTO.builder()
                .dailyUsersLast7Days(List.of(new ChartPoint("2026-03-01", BigDecimal.TEN)))
                .dailyRevenueLast7Days(List.of(new ChartPoint("2026-03-01", new BigDecimal("100.00"))))
                .streamsLast24Hours(List.of(new ChartPoint("03-01 12:00", BigDecimal.ONE)))
                .build();
        when(adminDashboardService.getCharts()).thenReturn(chartsDTO);

        mockMvc.perform(get("/api/admin/dashboard/charts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyUsersLast7Days[0].label").value("2026-03-01"))
                .andExpect(jsonPath("$.dailyUsersLast7Days[0].value").value(10))
                .andExpect(jsonPath("$.dailyRevenueLast7Days[0].value").value(100.00))
                .andExpect(jsonPath("$.streamsLast24Hours[0].label").value("03-01 12:00"));
    }
}








