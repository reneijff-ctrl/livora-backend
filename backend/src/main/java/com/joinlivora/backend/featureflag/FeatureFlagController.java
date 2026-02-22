package com.joinlivora.backend.featureflag;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/flags")
@RequiredArgsConstructor
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;
    private final ExperimentService experimentService;
    private final UserService userService;

    @GetMapping("/evaluate/{key}")
    public ResponseEntity<Map<String, Object>> evaluateFlag(
            @PathVariable String key,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userDetails != null ? userService.getByEmail(userDetails.getUsername()) : null;
        boolean enabled = featureFlagService.isEnabled(key, user);
        
        Map<String, Object> result = new HashMap<>();
        result.put("key", key);
        result.put("enabled", enabled);
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/experiment/{key}")
    public ResponseEntity<Map<String, Object>> getExperimentVariant(
            @PathVariable String key,
            @AuthenticationPrincipal UserDetails userDetails,
            @CookieValue(name = "livora_funnel_id", required = false) String funnelId
    ) {
        User user = userDetails != null ? userService.getByEmail(userDetails.getUsername()) : null;
        String variant = experimentService.getVariant(key, user, funnelId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("key", key);
        result.put("variant", variant);
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> evaluateBatch(
            @RequestBody List<String> keys,
            @AuthenticationPrincipal UserDetails userDetails,
            @CookieValue(name = "livora_funnel_id", required = false) String funnelId
    ) {
        User user = userDetails != null ? userService.getByEmail(userDetails.getUsername()) : null;
        Map<String, Object> results = new HashMap<>();
        
        for (String key : keys) {
            FeatureFlag flag = featureFlagService.getFlag(key);
            if (flag != null && flag.isExperiment()) {
                results.put(key, experimentService.getVariant(key, user, funnelId));
            } else {
                results.put(key, featureFlagService.isEnabled(key, user));
            }
        }
        
        return ResponseEntity.ok(results);
    }
}
