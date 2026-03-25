package com.joinlivora.backend.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AmlResult {
    private int riskScore;
    private List<String> triggeredRules;
}
