package com.joinlivora.backend.streaming;

import com.joinlivora.backend.streaming.service.ViewerLoadSheddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Internal endpoint for CDN failure mode management.
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li><b>Cloudflare Health Check Alert</b>: Configure a Cloudflare notification webhook
 *       pointing to {@code POST /api/internal/cdn-failure/activate} when origin health
 *       checks fail. The alert fires within ~30s of CDN-origin connectivity loss.</li>
 *   <li><b>Manual operator action</b>: Call {@code POST /api/internal/cdn-failure/activate}
 *       from the admin dashboard during an incident.</li>
 *   <li><b>Recovery</b>: Call {@code POST /api/internal/cdn-failure/deactivate} once
 *       Cloudflare health checks pass again, or wait for the 10-minute TTL to expire.</li>
 * </ul>
 *
 * <h2>Security</h2>
 * <p>All endpoints require {@code ROLE_ADMIN}. The Cloudflare webhook should be configured
 * with a shared Bearer token validated by Spring Security.
 */
@RestController
@RequestMapping("/api/internal/cdn-failure")
@RequiredArgsConstructor
@Slf4j
public class CdnFailureController {

    private final ViewerLoadSheddingService loadSheddingService;

    /**
     * Activates CDN failure mode — forces quality reduction to 480p for all live streams.
     * Safe to call multiple times (idempotent Redis SET).
     */
    @PostMapping("/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> activate() {
        loadSheddingService.activateCdnFailureMode();
        log.warn("[CDN_FAILURE_CONTROLLER] CDN failure mode activated by admin request");
        return ResponseEntity.ok("CDN failure mode activated — quality reduced to 480p for all streams");
    }

    /**
     * Deactivates CDN failure mode and restores full quality.
     */
    @PostMapping("/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deactivate() {
        loadSheddingService.deactivateCdnFailureMode();
        log.info("[CDN_FAILURE_CONTROLLER] CDN failure mode deactivated by admin request");
        return ResponseEntity.ok("CDN failure mode deactivated — full quality restored");
    }

    /**
     * Returns the current CDN failure mode status.
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> status() {
        boolean active = loadSheddingService.isCdnFailureModeActive();
        return ResponseEntity.ok(active ? "ACTIVE" : "INACTIVE");
    }
}
