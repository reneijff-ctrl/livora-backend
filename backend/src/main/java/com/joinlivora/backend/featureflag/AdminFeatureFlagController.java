package com.joinlivora.backend.featureflag;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/flags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeatureFlagController {

    private final FeatureFlagService featureFlagService;
    private final FeatureFlagRepository repository;
    private final AuditService auditService;
    private final UserService userService;

    @GetMapping
    public List<FeatureFlag> getAllFlags() {
        return repository.findAll();
    }

    @PostMapping
    public FeatureFlag createFlag(
            @RequestBody FeatureFlagDto dto,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        FeatureFlag saved = featureFlagService.saveFlag(dto.toEntity());
        
        User admin = userService.getByEmail(adminDetails.getUsername());
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                "FEATURE_FLAG_CREATED",
                "FEATURE_FLAG",
                null,
                Map.of("key", dto.getKey(), "enabled", dto.isEnabled()),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        
        return saved;
    }

    @PutMapping("/{key}")
    public ResponseEntity<FeatureFlag> updateFlag(
            @PathVariable String key,
            @RequestBody FeatureFlagDto dto,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        return repository.findByKey(key)
                .map(flag -> {
                    flag.setEnabled(dto.isEnabled());
                    flag.setRolloutPercentage(dto.getRolloutPercentage());
                    if (dto.getEnvironment() != null) {
                        flag.setEnvironment(dto.getEnvironment());
                    }
                    flag.setExperiment(dto.isExperiment());
                    FeatureFlag saved = featureFlagService.saveFlag(flag);
                    
                    User admin = userService.getByEmail(adminDetails.getUsername());
                    auditService.logEvent(
                            new UUID(0L, admin.getId()),
                            "FEATURE_FLAG_UPDATED",
                            "FEATURE_FLAG",
                            null,
                            Map.of("key", key, "enabled", dto.isEnabled(), "rolloutPercentage", dto.getRolloutPercentage()),
                            httpRequest.getRemoteAddr(),
                            httpRequest.getHeader("User-Agent")
                    );
                    
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deleteFlag(
            @PathVariable String key,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        featureFlagService.deleteFlag(key);
        
        User admin = userService.getByEmail(adminDetails.getUsername());
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                "FEATURE_FLAG_DELETED",
                "FEATURE_FLAG",
                null,
                Map.of("key", key),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        
        return ResponseEntity.noContent().build();
    }
}
