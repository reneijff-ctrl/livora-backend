package com.joinlivora.backend.analytics;

import lombok.*;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentAnalyticsId implements Serializable {
    private String experimentKey;
    private String variant;
}
