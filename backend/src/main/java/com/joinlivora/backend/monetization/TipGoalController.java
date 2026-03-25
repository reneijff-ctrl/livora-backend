package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.TipGoalDto;
import com.joinlivora.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/creator/tip-goals")
@RequiredArgsConstructor
@Slf4j
public class TipGoalController {

    private final TipGoalService tipGoalService;

    @PostMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<TipGoalDto> createGoal(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody TipGoalDto dto) {
        log.info("TIP_GOAL: Creating new goal for creator {}", principal.getUserId());
        return ResponseEntity.ok(tipGoalService.createGoal(principal.getUserId(), dto));
    }

    @GetMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<List<TipGoalDto>> getGoals(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(tipGoalService.getGoals(principal.getUserId()));
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<List<TipGoalDto>> reorderGoals(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody List<UUID> goalIds) {
        log.info("TIP_GOAL: Reordering goals for creator {}", principal.getUserId());
        return ResponseEntity.ok(tipGoalService.reorderGoals(principal.getUserId(), goalIds));
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<TipGoalDto> getActiveGoal(@AuthenticationPrincipal UserPrincipal principal) {
        return tipGoalService.getActiveGoal(principal.getUserId())
                .map(goal -> ResponseEntity.ok(tipGoalService.mapToDto(goal)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<TipGoalDto> updateGoal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody TipGoalDto dto) {
        log.info("TIP_GOAL: Updating goal {} for creator {}", id, principal.getUserId());
        return ResponseEntity.ok(tipGoalService.updateGoal(id, principal.getUserId(), dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> deleteGoal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        log.info("TIP_GOAL: Deleting goal {} for creator {}", id, principal.getUserId());
        tipGoalService.deleteGoal(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public/{creatorUserId}")
    public ResponseEntity<TipGoalDto> getPublicGoal(@PathVariable Long creatorUserId) {
        return tipGoalService.getActiveGoal(creatorUserId)
                .map(goal -> ResponseEntity.ok(tipGoalService.mapToDto(goal)))
                .orElse(ResponseEntity.noContent().build());
    }
}
