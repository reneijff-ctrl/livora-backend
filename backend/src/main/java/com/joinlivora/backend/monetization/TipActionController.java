package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.TipActionDto;
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
@RequestMapping("/api/creator/tip-actions")
@RequiredArgsConstructor
@Slf4j
public class TipActionController {

    private final TipActionService tipActionService;

    @GetMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<List<TipActionDto>> getAllActions(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(tipActionService.getAllActions(principal.getUserId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<TipActionDto> createAction(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody TipActionDto dto) {
        log.info("TIP_ACTION: Creating new action for creator {}", principal.getUserId());
        return ResponseEntity.ok(tipActionService.createAction(principal.getUserId(), dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<TipActionDto> updateAction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody TipActionDto dto) {
        log.info("TIP_ACTION: Updating action {} for creator {}", id, principal.getUserId());
        return ResponseEntity.ok(tipActionService.updateAction(principal.getUserId(), id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> deleteAction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        log.info("TIP_ACTION: Deleting action {} for creator {}", id, principal.getUserId());
        tipActionService.deleteAction(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
