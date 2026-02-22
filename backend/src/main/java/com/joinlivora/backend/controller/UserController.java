package com.joinlivora.backend.controller;

import com.joinlivora.backend.creator.service.CreatorOnboardingService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CreatorOnboardingService creatorOnboardingService;

    @GetMapping("/me")
    public ResponseEntity<?> userInfo(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }
        User user = userService.getByEmail(principal.getName());
        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "role", user.getRole(),
                "fraudRiskLevel", user.getFraudRiskLevel()
        ));
    }

    @PostMapping("/upgrade-to-creator")
    public ResponseEntity<?> upgradeToCreator(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }
        userService.upgradeToCreator(principal.getName());
        creatorOnboardingService.onboardCurrentUser();
        return ResponseEntity.ok(Map.of("message", "Successfully upgraded to Creator role"));
    }

    @PostMapping("/profile")
    public String updateProfile() {
        return "✅ Profile updated";
    }

    @GetMapping("/csrf")
    public void getCsrf() {
        // Just to trigger CSRF cookie generation if needed
    }
}
