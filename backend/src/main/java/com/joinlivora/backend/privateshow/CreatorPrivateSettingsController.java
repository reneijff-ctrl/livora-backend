package com.joinlivora.backend.privateshow;

import com.joinlivora.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/private-settings")
@RequiredArgsConstructor
public class CreatorPrivateSettingsController {

    private final CreatorPrivateSettingsService service;

    @GetMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorPrivateSettings> get(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.getOrCreate(principal.getUserId()));
    }

    @GetMapping("/by-creator/{creatorId}")
    public ResponseEntity<CreatorPrivateSettings> getByCreator(@PathVariable Long creatorId) {
        return ResponseEntity.ok(service.getOrCreate(creatorId));
    }

    @PatchMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorPrivateSettings> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        boolean enabled = toBoolean(body.get("enabled"), false);
        Long price = toLong(body.get("pricePerMinute"), 50L);

        boolean allowSpy = toBoolean(body.get("allowSpyOnPrivate"), false);
        Long spyPrice = body.containsKey("spyPricePerMinute") ? toLong(body.get("spyPricePerMinute"), null) : null;
        Integer maxSpy = body.containsKey("maxSpyViewers") ? toInteger(body.get("maxSpyViewers"), null) : null;

        if (price != null && price < 0) {
            return ResponseEntity.badRequest().build();
        }
        if (spyPrice != null && spyPrice < 0) {
            return ResponseEntity.badRequest().build();
        }
        if (maxSpy != null && maxSpy <= 0) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(service.updateWithSpy(principal.getUserId(), enabled, price, allowSpy, spyPrice, maxSpy));
    }

    private static boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    private static Long toLong(Object value, Long defaultValue) {
        if (value == null) return defaultValue;
        String s = value.toString().trim();
        if (s.isEmpty()) return defaultValue;
        try {
            return Long.valueOf(Double.valueOf(s).longValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Integer toInteger(Object value, Integer defaultValue) {
        if (value == null) return defaultValue;
        String s = value.toString().trim();
        if (s.isEmpty()) return defaultValue;
        try {
            return Integer.valueOf(Double.valueOf(s).intValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
