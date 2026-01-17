package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/creator/dashboard")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CREATOR') or hasRole('ADMIN')")
public class CreatorDashboardController {

    private final MonetizationService monetizationService;
    private final CreatorEarningRepository creatorEarningRepository;
    private final UserService userService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(@AuthenticationPrincipal UserDetails userDetails) {
        User creator = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(monetizationService.getCreatorStats(creator));
    }

    @GetMapping("/earnings")
    public ResponseEntity<List<CreatorEarning>> getEarnings(@AuthenticationPrincipal UserDetails userDetails) {
        User creator = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(creator));
    }
}
