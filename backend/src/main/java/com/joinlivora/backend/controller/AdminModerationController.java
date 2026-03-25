package com.joinlivora.backend.controller;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.user.dto.ModerationActionRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminModerationController {

    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<Page<User>> getUsers(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @GetMapping("/creators")
    public ResponseEntity<Page<User>> getCreators(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.getAllCreators(pageable));
    }

    @PostMapping("/users/{id}/suspend")
    public ResponseEntity<Void> suspendUser(
            @PathVariable Long id,
            @RequestBody(required = false) ModerationActionRequest request,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        User admin = userService.getByEmail(adminDetails.getUsername());
        String reason = (request != null) ? request.getReason() : null;
        userService.suspendUser(id, admin, reason, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{id}/unsuspend")
    public ResponseEntity<Void> unsuspendUser(
            @PathVariable Long id,
            @RequestBody(required = false) ModerationActionRequest request,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        User admin = userService.getByEmail(adminDetails.getUsername());
        String reason = (request != null) ? request.getReason() : null;
        userService.unsuspendUser(id, admin, reason, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/creators/{id}/shadowban")
    public ResponseEntity<Void> shadowbanCreator(
            @PathVariable UUID id,
            @RequestBody(required = false) ModerationActionRequest request,
            @AuthenticationPrincipal UserDetails adminDetails,
            HttpServletRequest httpRequest
    ) {
        User admin = userService.getByEmail(adminDetails.getUsername());
        String reason = (request != null) ? request.getReason() : null;
        userService.shadowbanCreator(id, admin, reason, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok().build();
    }
}
