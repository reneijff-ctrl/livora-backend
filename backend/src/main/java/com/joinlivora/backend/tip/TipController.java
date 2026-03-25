package com.joinlivora.backend.tip;

import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController("directTipController")
@RequestMapping("/api/tips")
@RequiredArgsConstructor
@Slf4j
public class TipController {

    private final TipService tipService;
    private final UserService userService;
    private final com.joinlivora.backend.creator.repository.CreatorProfileRepository creatorProfileRepository;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> createTip(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> payload
    ) {
        try {
            Long creatorId = Long.valueOf(payload.get("creator").toString());
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "Amount must be greater than 0"));
            }

            // Validate creator exists
            var creatorProfileOpt = creatorProfileRepository.findByUserId(creatorId);
            if (creatorProfileOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Creator not found"));
            }
            CreatorProfile creatorProfile = creatorProfileOpt.get();

            User fromUser = userService.getByEmail(userDetails.getUsername());

            DirectTip tip = DirectTip.builder()
                    .user(fromUser)
                    .creator(creatorProfile.getUser())
                    .amount(amount)
                    .currency("EUR") // Default currency
                    .status(TipStatus.PENDING)
                    .build();

            tipService.saveTip(tip);

            return ResponseEntity.ok(Map.of("message", "Tip created", "tipId", tip.getId()));
        } catch (NumberFormatException | NullPointerException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid request payload"));
        } catch (Exception e) {
            log.error("Failed to create tip", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "An unexpected error occurred"));
        }
    }
}
