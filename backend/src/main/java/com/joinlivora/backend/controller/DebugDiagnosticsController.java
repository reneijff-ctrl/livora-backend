package com.joinlivora.backend.controller;

import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// DIAGNOSTICS START
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugDiagnosticsController {

    private final LiveViewerCounterService liveViewerCounterService;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/viewers/{creatorUserId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getViewerDiagnostics(@PathVariable Long creatorUserId) {
        long viewerCount = liveViewerCounterService.getViewerCount(creatorUserId);
        String redisKey = "livestream:viewers:" + creatorUserId;
        
        Map<Object, Object> sessionRegistry = redisTemplate.opsForHash().entries("webrtc:sessions");
        
        return Map.of(
            "redisCount", viewerCount,
            "redisKey", redisKey,
            "sessionRegistry", sessionRegistry,
            "activeSessionsCount", sessionRegistry.size()
        );
    }
}
// DIAGNOSTICS END
