package com.joinlivora.backend.controller;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<?> userInfo(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "role", user.getRole(),
                "fraudRiskLevel", user.getFraudRiskLevel()
        ));
    }

    @PatchMapping("/username")
    public ResponseEntity<?> updateUsername(@RequestBody Map<String, String> body, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));

        String newUsername = body.get("username");
        try {
            User updated = userService.updateUsername(user, newUsername);
            return ResponseEntity.ok(Map.of(
                    "username", updated.getUsername(),
                    "message", "Username updated successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
