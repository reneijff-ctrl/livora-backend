package com.joinlivora.backend.featureflag;

import lombok.Data;

@Data
public class FeatureFlagDto {
    private String key;
    private boolean enabled;
    private int rolloutPercentage;
    private FeatureEnvironment environment;
    private boolean experiment;

    public static FeatureFlagDto fromEntity(FeatureFlag entity) {
        FeatureFlagDto dto = new FeatureFlagDto();
        dto.setKey(entity.getKey());
        dto.setEnabled(entity.isEnabled());
        dto.setRolloutPercentage(entity.getRolloutPercentage());
        dto.setEnvironment(entity.getEnvironment());
        dto.setExperiment(entity.isExperiment());
        return dto;
    }

    public FeatureFlag toEntity() {
        return FeatureFlag.builder()
                .key(this.key)
                .enabled(this.enabled)
                .rolloutPercentage(this.rolloutPercentage)
                .environment(this.environment != null ? this.environment : FeatureEnvironment.PROD)
                .experiment(this.experiment)
                .build();
    }
}
