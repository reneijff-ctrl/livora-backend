package com.joinlivora.backend.controller;

import com.joinlivora.backend.payout.CreatorSettingsService;
import com.joinlivora.backend.payout.dto.CreatorSettingsDto;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/creator/settings")
@RequiredArgsConstructor
public class CreatorSettingsController {

    private final UserService userService;
    private final CreatorSettingsService creatorSettingsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<CreatorSettingsDto> getSettings(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(creatorSettingsService.getSettings(user));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CreatorSettingsDto> updateSettings(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreatorSettingsDto settingsDto) {
        User user = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(creatorSettingsService.updateSettings(user, settingsDto));
    }
}
