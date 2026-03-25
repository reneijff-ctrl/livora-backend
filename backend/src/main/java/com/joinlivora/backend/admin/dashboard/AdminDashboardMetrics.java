package com.joinlivora.backend.admin.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class AdminDashboardMetrics {
    private long totalCreators;
    private long verifiedCreators;
    private long pendingApplications;
    private long activeFreezes;
    private long pendingPayouts;
    private BigDecimal pendingAmount;
    private long onlineUsers;
    private long activeStreams;
    private long newUsersToday;
    private BigDecimal todayRevenue;
    private long openReports;
    private long auditEvents24h;
    private long websocketSessions;
    private long pendingVerifications;
}
