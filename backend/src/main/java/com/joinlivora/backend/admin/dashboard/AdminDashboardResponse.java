package com.joinlivora.backend.admin.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponse {
    private AdminDashboardMetrics metrics;
    private AdminDashboardChartsDTO charts;
}
