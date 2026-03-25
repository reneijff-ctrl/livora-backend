package com.joinlivora.backend.admin.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@AllArgsConstructor
@Getter
public class ChartDataPoint {
    private LocalDate date;
    private BigDecimal value;
}
