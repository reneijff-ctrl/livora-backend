package com.joinlivora.backend.monetization.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollusionOverrideRequest {
    private int score;
    private String reason;
}
