package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.TipGoalGroupDto;
import com.joinlivora.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/creator/tip-goal-groups")
@RequiredArgsConstructor
@Slf4j
public class TipGoalGroupController {

    private final TipGoalGroupService groupService;

    @GetMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<List<TipGoalGroupDto>> getGroups(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(groupService.getGroups(principal.getUserId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<TipGoalGroupDto> createGroup(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody TipGoalGroupDto dto) {
        log.info("TIP_GOAL_GROUP: Creating group for creator {}", principal.getUserId());
        return ResponseEntity.ok(groupService.createGroup(principal.getUserId(), dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<TipGoalGroupDto> updateGroup(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody TipGoalGroupDto dto) {
        log.info("TIP_GOAL_GROUP: Updating group {} for creator {}", id, principal.getUserId());
        return ResponseEntity.ok(groupService.updateGroup(id, principal.getUserId(), dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> deleteGroup(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        log.info("TIP_GOAL_GROUP: Deleting group {} for creator {}", id, principal.getUserId());
        groupService.deleteGroup(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/milestones")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<TipGoalGroupDto> addMilestone(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        Long targetAmount = ((Number) body.get("targetAmount")).longValue();
        log.info("TIP_GOAL_GROUP: Adding milestone to group {} for creator {}", id, principal.getUserId());
        return ResponseEntity.ok(groupService.addMilestone(id, principal.getUserId(), title, targetAmount));
    }

    @DeleteMapping("/milestones/{milestoneId}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> deleteMilestone(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID milestoneId) {
        log.info("TIP_GOAL_GROUP: Deleting milestone {} for creator {}", milestoneId, principal.getUserId());
        groupService.deleteMilestone(milestoneId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reset")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> resetGoalGroup(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        log.info("TIP_GOAL_GROUP: Resetting group {} for creator {}", id, principal.getUserId());
        groupService.resetGroup(id, principal.getUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<TipGoalGroupDto> getActiveGroup(@AuthenticationPrincipal UserPrincipal principal) {
        return groupService.getActiveGroup(principal.getUserId())
                .map(group -> ResponseEntity.ok(groupService.mapToDto(group)))
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/public/{creatorUserId}")
    public ResponseEntity<TipGoalGroupDto> getPublicActiveGroup(@PathVariable Long creatorUserId) {
        return groupService.getActiveGroup(creatorUserId)
                .map(group -> ResponseEntity.ok(groupService.mapToDto(group)))
                .orElse(ResponseEntity.noContent().build());
    }
}
