package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutStatusDTO;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/creator/payout")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
public class CreatorPayoutStatusController {

    private final CreatorPayoutService creatorPayoutService;
    private final UserService userService;

    @GetMapping("/status")
    public ResponseEntity<PayoutStatusDTO> getPayoutStatus(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("CREATOR_PAYOUT: Payout status requested by: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(creatorPayoutService.getPayoutStatus(user));
    }

    @PostMapping("/request")
    public ResponseEntity<CreatorPayout> requestPayout(@AuthenticationPrincipal UserDetails userDetails) throws Exception {
        log.info("CREATOR_PAYOUT: Payout request initiated by: {}", userDetails.getUsername());
        User user = userService.getByEmail(userDetails.getUsername());
        CreatorPayout payout = creatorPayoutService.requestPayout(user);
        return ResponseEntity.ok(payout);
    }
}
