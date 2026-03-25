package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutHoldStatusDTO;
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

@RestController
@RequestMapping("/api/creator/payout-hold-status")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CREATOR') or hasRole('ADMIN')")
public class CreatorPayoutHoldController {

    private final PayoutHoldService payoutHoldService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<PayoutHoldStatusDTO> getStatus(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByEmail(userDetails.getUsername());
        log.debug("Fetching payout hold status for creator: {}", user.getEmail());
        return ResponseEntity.ok(payoutHoldService.getPayoutHoldStatus(user));
    }
}
