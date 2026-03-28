package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.TipMenuCategoryDto;
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
@RequestMapping("/api/creator/tip-menu-categories")
@RequiredArgsConstructor
@Slf4j
public class TipMenuCategoryController {

    private final TipMenuCategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<List<TipMenuCategoryDto>> getCategories(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(categoryService.getCategories(principal.getUserId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<TipMenuCategoryDto> createCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody TipMenuCategoryDto dto) {
        log.info("TIP_MENU_CATEGORY: Creating category for creator {}", principal.getUserId());
        return ResponseEntity.ok(categoryService.createCategory(principal.getUserId(), dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<TipMenuCategoryDto> updateCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody TipMenuCategoryDto dto) {
        log.info("TIP_MENU_CATEGORY: Updating category {} for creator {}", id, principal.getUserId());
        return ResponseEntity.ok(categoryService.updateCategory(principal.getUserId(), id, dto));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<TipMenuCategoryDto> toggleCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        log.info("TIP_MENU_CATEGORY: Toggling category {} for creator {}", id, principal.getUserId());
        return ResponseEntity.ok(categoryService.toggleEnabled(principal.getUserId(), id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> deleteCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        log.info("TIP_MENU_CATEGORY: Deleting category {} for creator {}", id, principal.getUserId());
        categoryService.deleteCategory(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
