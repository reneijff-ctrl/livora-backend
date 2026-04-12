package com.joinlivora.backend.admin.controller;

import com.joinlivora.backend.admin.dto.AdminUserDto;
import com.joinlivora.backend.user.AdminRole;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('ADMIN_MANAGE')")
    public ResponseEntity<List<AdminUserDto>> getAllAdmins() {
        List<AdminUserDto> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(admins);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN') and @securityService.hasPermission('ADMIN_MANAGE')")
    public ResponseEntity<AdminUserDto> updateAdminRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() != Role.ADMIN) {
            return ResponseEntity.badRequest().build();
        }
        AdminRole newRole = AdminRole.valueOf(body.get("adminRole"));
        user.setAdminRole(newRole);
        userRepository.save(user);
        return ResponseEntity.ok(toDto(user));
    }

    private AdminUserDto toDto(User user) {
        return AdminUserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .adminRole(user.getAdminRole())
                .build();
    }
}
