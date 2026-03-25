package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.adaptive.DynamicTipRecommendationService;
import com.joinlivora.backend.analytics.adaptive.dto.TipRecommendationResponse;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/creator/tip")
@RequiredArgsConstructor
public class CreatorTipRecommendationController {

    private final DynamicTipRecommendationService tipRecommendationService;
    private final UserService userService;

    @GetMapping("/recommendation")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<TipRecommendationResponse> getRecommendation(Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        TipRecommendationResponse response = tipRecommendationService.generateForCreator(user);
        return ResponseEntity.ok(response);
    }
}
