package com.joinlivora.backend.aml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmlResult {
    private int riskScore;
    private String riskLevel;
    private List<String> triggeredRules;
}
