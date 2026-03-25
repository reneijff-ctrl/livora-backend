package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.auth.AuthService;
import com.joinlivora.backend.auth.dto.UserMeResponse;
import com.joinlivora.backend.creator.dto.CreatorApplicationResponse;
import com.joinlivora.backend.creator.dto.CreatorProfileDTO;
import com.joinlivora.backend.creator.dto.SubmitApplicationRequest;
import com.joinlivora.backend.creator.dto.onboarding.Step1BasicsRequest;
import com.joinlivora.backend.creator.dto.onboarding.Step2ProfileRequest;
import com.joinlivora.backend.creator.dto.onboarding.Step3VerificationRequest;
import com.joinlivora.backend.creator.model.CreatorApplication;
import com.joinlivora.backend.creator.service.CreatorApplicationService;
import com.joinlivora.backend.creator.service.CreatorOnboardingService;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.payment.dto.SubscriptionResponse;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.token.TokenWalletService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/creator/onboarding")
@RequiredArgsConstructor
@Slf4j
public class CreatorOnboardingController {

    private final CreatorApplicationService creatorApplicationService;
    private final CreatorOnboardingService creatorOnboardingService;
    private final UserService userService;
    private final JwtService jwtService;
    private final AuthService authService;
    private final TokenWalletService tokenWalletService;
    private final CreatorProfileService creatorProfileService;

    @PostMapping("/basics")
    public ResponseEntity<Void> saveBasics(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Step1BasicsRequest request) {
        creatorOnboardingService.saveStep1Basics(principal.getUserId(), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/profile")
    public ResponseEntity<Void> saveProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Step2ProfileRequest request) {
        creatorOnboardingService.saveStep2Profile(principal.getUserId(), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verification")
    public ResponseEntity<Void> saveVerification(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Step3VerificationRequest request) {
        creatorOnboardingService.saveStep3Verification(principal.getUserId(), request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/payout-status")
    public ResponseEntity<Boolean> getPayoutStatus(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(creatorOnboardingService.checkStep4Payout(principal.getUserId()));
    }

    @PostMapping("/start")
    public ResponseEntity<CreatorApplicationResponse> startApplication(@AuthenticationPrincipal UserPrincipal principal) {
        CreatorApplication application = creatorApplicationService.startApplication(principal.getUserId());
        return ResponseEntity.ok(mapToResponse(application));
    }

    @PostMapping("/submit")
    public ResponseEntity<CreatorApplicationResponse> submitApplication(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody SubmitApplicationRequest request) {
        CreatorApplication application = creatorApplicationService.submitApplication(
                principal.getUserId(),
                request.isTermsAccepted(),
                request.isAgeVerified()
        );
        return ResponseEntity.ok(mapToResponse(application));
    }

    @GetMapping("/status")
    public ResponseEntity<CreatorApplicationResponse> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        return creatorApplicationService.getApplication(principal.getUserId())
                .map(application -> ResponseEntity.ok(mapToResponse(application)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Legacy upgrade method for compatibility. 
     * Now mapped to /api/creator/onboarding/upgrade-and-auth
     */
    @PostMapping("/upgrade-and-auth")
    @Transactional
    public ResponseEntity<UpgradeAndAuthResponse> upgradeAndAuth(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = principal.getEmail();
        log.info("CREATOR_ONBOARDING: Upgrading user {} to creator and generating new token", email);
        
        // 1. Upgrade user role (handles profile initialization internally)
        userService.upgradeToCreator(email);
        
        // 2. Reload updated user from database
        User user = userService.getByEmail(email);
        
        // 3. Generate new access token
        String accessToken = jwtService.generateAccessToken(user);
        
        // 4. Map user to UserMeResponse (our standard UserResponseDTO)
        SubscriptionResponse sub = authService.getSubscriptionForUser(user);
        long tokenBalance = tokenWalletService.getAvailableBalance(user.getId());
        CreatorProfileDTO creatorProfile = creatorProfileService.getProfileDTO(user).orElse(null);

        UserMeResponse userResponse = new UserMeResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                tokenBalance,
                sub,
                creatorProfile
        );

        return ResponseEntity.ok(new UpgradeAndAuthResponse(accessToken, userResponse));
    }

    private CreatorApplicationResponse mapToResponse(CreatorApplication application) {
        return CreatorApplicationResponse.builder()
                .status(application.getStatus())
                .submittedAt(application.getSubmittedAt())
                .approvedAt(application.getApprovedAt())
                .reviewNotes(application.getReviewNotes())
                .build();
    }

    public record UpgradeAndAuthResponse(
            String accessToken,
            UserMeResponse user
    ) {}
}
