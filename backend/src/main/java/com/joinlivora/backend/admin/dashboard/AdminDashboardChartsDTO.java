package com.joinlivora.backend.admin.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardChartsDTO {
    private List<ChartPoint> dailyUsersLast7Days;
    private List<ChartPoint> dailyRevenueLast7Days;
    private List<ChartPoint> streamsLast24Hours;
}
