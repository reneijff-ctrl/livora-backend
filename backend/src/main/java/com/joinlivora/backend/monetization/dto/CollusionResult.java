package com.joinlivora.backend.monetization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollusionResult {
    private int collusionScore;
    private List<String> patternTypes;
}
