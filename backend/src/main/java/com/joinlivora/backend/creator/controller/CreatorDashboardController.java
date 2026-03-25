package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.dto.CreatorDashboardDto;
import com.joinlivora.backend.creator.dto.CreatorDashboardStatisticsDTO;
import com.joinlivora.backend.creator.dto.CreatorDashboardSummary;
import com.joinlivora.backend.creator.follow.service.CreatorFollowService;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController("creatorDashboardControllerV2")
@RequestMapping("/api/creator/dashboard")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class CreatorDashboardController {

    private final UserService userService;
    private final CreatorProfileService creatorProfileService;
    private final CreatorFollowService followService;
    private final com.joinlivora.backend.payout.CreatorDashboardService dashboardService;
    private final com.joinlivora.backend.payout.CreatorEarningsService creatorEarningsService;
    private final com.joinlivora.backend.payout.CreatorEarningRepository creatorEarningRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<CreatorDashboardDto> getDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("DASHBOARD: Fetching creator dashboard for user ID: {}", principal.getUserId());
        User user = userService.getById(principal.getUserId());
        
        // Ensure profile exists and get it
        CreatorProfile profile = creatorProfileService.initializeCreatorProfile(user);
        var profileDto = creatorProfileService.mapToDTO(profile);

        // Get real stats from legacy service with safety fallback
        com.joinlivora.backend.creator.dto.CreatorDashboardStatsDto stats;
        long totalFollowers = followService.getFollowerCount(user);
        try {
            var legacyStats = dashboardService.getDashboard(user);
            stats = com.joinlivora.backend.creator.dto.CreatorDashboardStatsDto.builder()
                    .totalEarnings(legacyStats.getTotalEarnings() != null ? legacyStats.getTotalEarnings() : BigDecimal.ZERO)
                    .totalFollowers(totalFollowers)
                    .isVerified(false)   // Placeholder
                    .availableBalance(legacyStats.getAvailableBalance() != null ? legacyStats.getAvailableBalance() : BigDecimal.ZERO)
                    .activeStreams((int) legacyStats.getActiveStreams())
                    .contentCount(legacyStats.getContentCount())
                    .status(profileDto.getStatus() != null ? profileDto.getStatus().name() : "DRAFT")
                    .build();
        } catch (Exception e) {
            log.warn("DASHBOARD: Failed to fetch legacy stats for user {}: {}", principal.getUserId(), e.getMessage());
            stats = com.joinlivora.backend.creator.dto.CreatorDashboardStatsDto.builder()
                    .totalEarnings(BigDecimal.ZERO)
                    .totalFollowers(totalFollowers)
                    .isVerified(false)
                    .availableBalance(BigDecimal.ZERO)
                    .activeStreams(0)
                    .contentCount(0L)
                    .status(profileDto.getStatus() != null ? profileDto.getStatus().name() : "DRAFT")
                    .build();
        }

        return ResponseEntity.ok(CreatorDashboardDto.builder()
                .creatorProfile(profileDto)
                .stats(stats)
                .build());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<CreatorDashboardSummary> getSummary(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.getById(principal.getUserId());
        return ResponseEntity.ok(creatorProfileService.getDashboardSummary(user));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorDashboardStatisticsDTO> getStatistics(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.getById(principal.getUserId());
        log.info("DASHBOARD: User {} accessed dashboard statistics", user.getEmail());
        return ResponseEntity.ok(dashboardService.getStatistics(user));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> getStats(@AuthenticationPrincipal UserPrincipal principal) {
        User creator = userService.getById(principal.getUserId());
        log.info("AUDIT: User {} accessed dashboard stats for creator ID {}", principal.getUsername(), creator.getId());
        return ResponseEntity.ok(creatorEarningsService.getCreatorStats(creator));
    }

    @GetMapping("/earnings")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<java.util.List<com.joinlivora.backend.payout.dto.CreatorEarningDto>> getEarnings(@AuthenticationPrincipal UserPrincipal principal) {
        User creator = userService.getById(principal.getUserId());
        log.info("AUDIT: User {} accessed earning history for creator ID {}", principal.getUsername(), creator.getId());
        
        java.util.List<com.joinlivora.backend.payout.dto.CreatorEarningDto> earnings = creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(creator)
                .stream()
                .map(this::mapEarningToDto)
                .collect(java.util.stream.Collectors.toList());
                
        return ResponseEntity.ok(earnings);
    }

    private com.joinlivora.backend.payout.dto.CreatorEarningDto mapEarningToDto(com.joinlivora.backend.payout.CreatorEarning entity) {
        return com.joinlivora.backend.payout.dto.CreatorEarningDto.builder()
                .id(entity.getId())
                .grossAmount(entity.getGrossAmount())
                .platformFee(entity.getPlatformFee())
                .netAmount(entity.getNetAmount())
                .currency(entity.getCurrency())
                .sourceType(entity.getSourceType())
                .stripeChargeId(entity.getStripeChargeId())
                .locked(entity.isLocked())
                .createdAt(entity.getCreatedAt())
                .status(entity.isLocked() ? "LOCKED" : "AVAILABLE")
                .build();
    }
}
