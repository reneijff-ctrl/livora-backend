package com.joinlivora.backend.controller;

import com.joinlivora.backend.payout.CreatorDashboardService;
import com.joinlivora.backend.payout.dto.CreatorDashboardDto;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/creator")
@RequiredArgsConstructor
public class CreatorDashboardController {

    private final UserService userService;
    private final CreatorDashboardService creatorDashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<CreatorDashboardDto> getCreatorDashboard(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        CreatorDashboardDto dashboard = creatorDashboardService.getDashboard(user);
        return ResponseEntity.ok(dashboard);
    }
}
