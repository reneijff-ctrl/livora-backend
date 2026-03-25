package com.joinlivora.backend.streaming;

import com.joinlivora.backend.admin.dto.AdminStreamDTO;
import com.joinlivora.backend.admin.dto.StreamRiskStatusDTO;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.fraud.service.FraudRiskScoreService;
import com.joinlivora.backend.privateshow.*;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.StreamRiskMonitorService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/streams")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminStreamsController {

    private final StreamService streamService;
    private final UserService userService;
    private final BadgeService badgeService;
    private final BadgeRepository badgeRepository;
    private final AuditService auditService;
    private final StreamRepository streamRepository;
    private final LiveViewerCounterService viewerCounterService;
    private final FraudRiskScoreService fraudRiskScoreService;
    private final StreamRiskMonitorService streamRiskMonitorService;
    private final PrivateSessionRepository privateSessionRepository;
    private final PrivateSpySessionRepository privateSpySessionRepository;
    private final CreatorPrivateSettingsService creatorPrivateSettingsService;

    @GetMapping({"", "/active"})
    public ResponseEntity<Page<AdminStreamDTO>> getActiveStreams(Pageable pageable) {
        Page<Stream> liveStreams = streamRepository.findActiveStreamsWithUser(pageable);
        Page<AdminStreamDTO> dtos = liveStreams.map(this::mapToAdminStreamDTO);
        return ResponseEntity.ok(dtos);
    }

    private AdminStreamDTO mapToAdminStreamDTO(Stream stream) {
        Long creatorId = stream.getCreator().getId();
        Instant startedAt = stream.getStartedAt() != null ? stream.getStartedAt() : stream.getCreatedAt();
        long durationSeconds = startedAt != null ? Duration.between(startedAt, Instant.now()).getSeconds() : 0;
        
        // Canonical viewer source is now Redis via LiveViewerCounterService
        int viewerCount = (int) viewerCounterService.getViewerCount(creatorId);
        int fraudRiskScore = fraudRiskScoreService.getLatestScore(creatorId);
        int messageRate = 0; // Placeholder for future message rate tracking

        // Private session info
        boolean privateActive = false;
        Long privatePricePerMinute = null;
        int activeSpyCount = 0;
        boolean spyEnabled = false;

        try {
            CreatorPrivateSettings settings = creatorPrivateSettingsService.getOrCreate(creatorId);
            spyEnabled = settings.isEnabled() && settings.isAllowSpyOnPrivate();
        } catch (Exception e) {
            log.debug("Could not load private settings for creator {}: {}", creatorId, e.getMessage());
        }

        var activeSession = privateSessionRepository
                .findFirstByCreator_IdAndStatusOrderByStartedAtDesc(creatorId, PrivateSessionStatus.ACTIVE);
        if (activeSession.isPresent()) {
            PrivateSession ps = activeSession.get();
            privateActive = true;
            privatePricePerMinute = ps.getPricePerMinute();
            activeSpyCount = privateSpySessionRepository.countByPrivateSession_IdAndStatus(ps.getId(), SpySessionStatus.ACTIVE);
        }

        return AdminStreamDTO.builder()
                .streamId(stream.getId())
                .creatorId(creatorId)
                .userId(creatorId)   // Compatibility for AdminLiveStreamsWidget.tsx
                .creator(creatorId)  // Compatibility for AdminLiveStreamsWidget.tsx
                .creatorUsername(stream.getCreator().getUsername())
                .title(stream.getTitle())
                .viewerCount(viewerCount)
                .startedAt(startedAt)
                .durationSeconds(durationSeconds)
                .fraudRiskScore(fraudRiskScore)
                .messageRate(messageRate)
                .privateActive(privateActive)
                .privatePricePerMinute(privatePricePerMinute)
                .spyEnabled(spyEnabled)
                .activeSpyCount(activeSpyCount)
                .build();
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Void> forceStopStream(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest request
    ) {
        Stream stream = streamRepository.findByIdWithCreator(id)
                .orElseThrow(() -> new RuntimeException("Stream not found"));
        
        streamService.stopStream(stream.getCreator(), "admin");
        
        User admin = userService.getByEmail(adminDetails.getUsername());
        auditService.logEvent(
                new UUID(0L, admin.getId()),
                AuditService.CONTENT_TAKEDOWN,
                "STREAM",
                id,
                Map.of("action", "force_stop", "creator", stream.getCreator().getId()),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
        
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/risk")
    public ResponseEntity<StreamRiskStatusDTO> getStreamRisk(@PathVariable UUID id) {
        return ResponseEntity.ok(streamRiskMonitorService.getStreamRisk(id));
    }

    @PostMapping("/badges")
    public ResponseEntity<Badge> createBadge(@RequestBody Map<String, Object> payload) {
        Badge badge = Badge.builder()
                .name((String) payload.get("name"))
                .tokenCost(Long.valueOf(payload.get("tokenCost").toString()))
                .durationDays(payload.get("durationDays") != null ? Integer.valueOf(payload.get("durationDays").toString()) : null)
                .build();
        return ResponseEntity.ok(badgeRepository.save(badge));
    }
}
