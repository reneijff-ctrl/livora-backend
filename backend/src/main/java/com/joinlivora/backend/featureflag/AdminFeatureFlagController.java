package com.joinlivora.backend.featureflag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/flags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeatureFlagController {

    private final FeatureFlagService featureFlagService;
    private final FeatureFlagRepository repository;

    @GetMapping
    public List<FeatureFlag> getAllFlags() {
        return repository.findAll();
    }

    @PostMapping
    public FeatureFlag createFlag(@RequestBody FeatureFlagDto dto) {
        return featureFlagService.saveFlag(dto.toEntity());
    }

    @PutMapping("/{key}")
    public ResponseEntity<FeatureFlag> updateFlag(@PathVariable String key, @RequestBody FeatureFlagDto dto) {
        return repository.findByKey(key)
                .map(flag -> {
                    flag.setEnabled(dto.isEnabled());
                    flag.setRolloutPercentage(dto.getRolloutPercentage());
                    if (dto.getEnvironment() != null) {
                        flag.setEnvironment(dto.getEnvironment());
                    }
                    flag.setExperiment(dto.isExperiment());
                    return ResponseEntity.ok(featureFlagService.saveFlag(flag));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deleteFlag(@PathVariable String key) {
        featureFlagService.deleteFlag(key);
        return ResponseEntity.noContent().build();
    }
}
