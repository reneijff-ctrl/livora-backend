package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutHoldOverrideRequest;
import com.joinlivora.backend.payout.dto.PayoutHoldReleaseRequest;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/payout-hold")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPayoutHoldController {

    private final PayoutHoldAdminService payoutHoldAdminService;
    private final UserService userService;

    @PostMapping("/override")
    public ResponseEntity<Void> overrideHold(
            @RequestBody PayoutHoldOverrideRequest request,
            @AuthenticationPrincipal UserDetails adminDetails
    ) {
        User admin = userService.getByEmail(adminDetails.getUsername());
        payoutHoldAdminService.overrideHold(request, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/release")
    public ResponseEntity<Void> releaseHold(
            @RequestBody PayoutHoldReleaseRequest request,
            @AuthenticationPrincipal UserDetails adminDetails
    ) {
        User admin = userService.getByEmail(adminDetails.getUsername());
        payoutHoldAdminService.releaseHold(request, admin);
        return ResponseEntity.noContent().build();
    }
}
