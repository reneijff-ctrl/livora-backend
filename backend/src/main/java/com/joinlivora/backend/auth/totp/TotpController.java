package com.joinlivora.backend.auth.totp;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/2fa")
public class TotpController {

    private static final Logger logger = LoggerFactory.getLogger(TotpController.class);

    private final TotpService totpService;
    private final TotpBackupCodeRepository backupCodeRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuditService auditService;

    public TotpController(TotpService totpService,
                          TotpBackupCodeRepository backupCodeRepository,
                          UserRepository userRepository,
                          JwtService jwtService,
                          AuditService auditService) {
        this.totpService = totpService;
        this.backupCodeRepository = backupCodeRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    /**
     * Exchange a valid pre-auth token + TOTP code for a full access token.
     * This is the only endpoint a pre_2fa scoped token can reach.
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verify(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam int code,
            HttpServletRequest request
    ) {
        String preAuthToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String clientIp = getClientIp(request);

        Claims claims;
        try {
            claims = jwtService.parseClaims(preAuthToken);
        } catch (Exception e) {
            logger.warn("SECURITY [2fa]: Invalid or expired pre-auth token");
            return ResponseEntity.status(401).body(Map.of("message", "Invalid or expired token."));
        }

        if (!"pre_2fa".equals(claims.get("scope", String.class))) {
            logger.warn("SECURITY [2fa]: Token presented to /verify does not have pre_2fa scope");
            return ResponseEntity.status(403).body(Map.of("message", "Invalid token scope."));
        }

        Long userId = Long.parseLong(claims.getSubject());
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found."));
        }

        if (!user.isTotpEnabled() || user.getTotpSecret() == null) {
            return ResponseEntity.status(400).body(Map.of("message", "2FA is not enabled for this account."));
        }

        boolean valid = totpService.verifyCode(user.getTotpSecret(), code);
        if (!valid) {
            logger.warn("SECURITY [2fa]: Invalid TOTP code for user: {}", user.getEmail());
            auditService.logEvent(
                    null,
                    AuditService.TOTP_VERIFY_FAILED,
                    "USER",
                    null,
                    Map.of("email", user.getEmail()),
                    clientIp,
                    request.getHeader("User-Agent")
            );
            return ResponseEntity.status(401).body(Map.of("message", "Invalid authentication code."));
        }

        user.setTotpVerifiedAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user);
        logger.info("SECURITY [2fa]: 2FA verified — full JWT issued for user: {}", user.getEmail());

        return ResponseEntity.ok(Map.of("accessToken", accessToken));
    }

    /**
     * Initiate TOTP setup using a pre-auth token (for admins who must set up 2FA before first login).
     */
    @PostMapping("/setup-init")
    public ResponseEntity<?> setupInit(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request
    ) {
        String preAuthToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

        Claims claims;
        try {
            claims = jwtService.parseClaims(preAuthToken);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid or expired token."));
        }

        if (!"pre_2fa".equals(claims.get("scope", String.class))) {
            return ResponseEntity.status(403).body(Map.of("message", "Invalid token scope."));
        }

        Long userId = Long.parseLong(claims.getSubject());
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found."));
        }

        if (user.isTotpEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("message", "2FA is already enabled."));
        }

        String encryptedSecret = totpService.generateSecret();
        user.setTotpSecret(encryptedSecret);
        userRepository.save(user);

        String qrUrl = totpService.getQRUrl(user.getEmail(), encryptedSecret);
        String plainSecret = totpService.decryptSecret(encryptedSecret);
        logger.info("SECURITY [2fa]: TOTP setup-init via pre-auth token for user: {}", user.getEmail());

        return ResponseEntity.ok(Map.of("secret", plainSecret, "qrUrl", qrUrl));
    }

    /**
     * Confirm TOTP setup using a pre-auth token. Enables 2FA and issues a full JWT.
     */
    @PostMapping("/setup-confirm")
    public ResponseEntity<?> setupConfirm(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam int code,
            HttpServletRequest request
    ) {
        String preAuthToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String clientIp = getClientIp(request);

        Claims claims;
        try {
            claims = jwtService.parseClaims(preAuthToken);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid or expired token."));
        }

        if (!"pre_2fa".equals(claims.get("scope", String.class))) {
            return ResponseEntity.status(403).body(Map.of("message", "Invalid token scope."));
        }

        Long userId = Long.parseLong(claims.getSubject());
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found."));
        }

        if (user.getTotpSecret() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "2FA setup not initiated. Call /setup-init first."));
        }

        boolean valid = totpService.verifyCode(user.getTotpSecret(), code);
        if (!valid) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid authentication code."));
        }

        user.setTotpEnabled(true);
        user.setTotpVerifiedAt(Instant.now());
        userRepository.save(user);

        List<String> backupCodes = totpService.generateBackupCodes(userId);

        String accessToken = jwtService.generateAccessToken(user);
        logger.info("SECURITY [2fa]: 2FA setup confirmed via pre-auth token — full JWT issued for user: {}", user.getEmail());

        auditService.logEvent(
                null,
                AuditService.TOTP_ENABLED,
                "USER",
                null,
                Map.of("email", user.getEmail()),
                clientIp,
                request.getHeader("User-Agent")
        );

        return ResponseEntity.ok(Map.of("accessToken", accessToken, "backupCodes", backupCodes));
    }

    /**
     * Generate a new TOTP secret and QR URL for setup.
     * Requires a fully authenticated user (2FA not yet enabled).
     */
    @PostMapping("/setup")
    public ResponseEntity<?> setup(
            @AuthenticationPrincipal com.joinlivora.backend.security.UserPrincipal principal,
            HttpServletRequest request
    ) {
        User user = userRepository.findById(principal.getUserId()).orElseThrow();

        if (user.isTotpEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("message", "2FA is already enabled."));
        }

        String encryptedSecret = totpService.generateSecret();
        user.setTotpSecret(encryptedSecret);
        userRepository.save(user);

        String qrUrl = totpService.getQRUrl(user.getEmail(), encryptedSecret);
        String plainSecret = totpService.decryptSecret(encryptedSecret);
        logger.info("SECURITY [2fa]: TOTP setup initiated for user: {}", user.getEmail());

        return ResponseEntity.ok(Map.of("secret", plainSecret, "qrUrl", qrUrl));
    }

    /**
     * Confirm TOTP setup by verifying the first code. Enables 2FA on the account.
     */
    @PostMapping("/enable")
    public ResponseEntity<?> enable(
            @AuthenticationPrincipal com.joinlivora.backend.security.UserPrincipal principal,
            @RequestParam int code,
            HttpServletRequest request
    ) {
        User user = userRepository.findById(principal.getUserId()).orElseThrow();
        String clientIp = getClientIp(request);

        if (user.isTotpEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("message", "2FA is already enabled."));
        }

        if (user.getTotpSecret() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "2FA setup not initiated. Call /setup first."));
        }

        boolean valid = totpService.verifyCode(user.getTotpSecret(), code);
        if (!valid) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid authentication code."));
        }

        user.setTotpEnabled(true);
        user.setTotpVerifiedAt(Instant.now());
        userRepository.save(user);

        List<String> backupCodes = totpService.generateBackupCodes(user.getId());

        auditService.logEvent(
                null,
                AuditService.TOTP_ENABLED,
                "USER",
                null,
                Map.of("email", user.getEmail()),
                clientIp,
                request.getHeader("User-Agent")
        );

        logger.info("SECURITY [2fa]: 2FA enabled for user: {}", user.getEmail());
        return ResponseEntity.ok(Map.of(
                "message", "Two-factor authentication enabled successfully.",
                "backupCodes", backupCodes
        ));
    }

    /**
     * Disable 2FA. Requires the current TOTP code to confirm intent.
     */
    @PostMapping("/disable")
    public ResponseEntity<?> disable(
            @AuthenticationPrincipal com.joinlivora.backend.security.UserPrincipal principal,
            @RequestParam int code,
            HttpServletRequest request
    ) {
        User user = userRepository.findById(principal.getUserId()).orElseThrow();
        String clientIp = getClientIp(request);

        if (!user.isTotpEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("message", "2FA is not enabled."));
        }

        boolean valid = totpService.verifyCode(user.getTotpSecret(), code);
        if (!valid) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid authentication code."));
        }

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpVerifiedAt(null);
        userRepository.save(user);

        backupCodeRepository.deleteAllByUserId(user.getId());

        auditService.logEvent(
                null,
                AuditService.TOTP_DISABLED,
                "USER",
                null,
                Map.of("email", user.getEmail()),
                clientIp,
                request.getHeader("User-Agent")
        );

        logger.info("SECURITY [2fa]: 2FA disabled for user: {}", user.getEmail());
        return ResponseEntity.ok(Map.of("message", "Two-factor authentication disabled."));
    }

    /**
     * Returns the current 2FA status for the authenticated user.
     */
    @GetMapping("/status")
    public ResponseEntity<?> status(@AuthenticationPrincipal com.joinlivora.backend.security.UserPrincipal principal) {
        User user = userRepository.findById(principal.getUserId()).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "totpEnabled", user.isTotpEnabled(),
                "totpVerifiedAt", user.getTotpVerifiedAt() != null ? user.getTotpVerifiedAt().toString() : null
        ));
    }

    /**
     * Verify a backup code in place of a TOTP code.
     */
    @PostMapping("/verify-backup")
    public ResponseEntity<?> verifyBackup(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String backupCode,
            HttpServletRequest request
    ) {
        String preAuthToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String clientIp = getClientIp(request);

        Claims claims;
        try {
            claims = jwtService.parseClaims(preAuthToken);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid or expired token."));
        }

        if (!"pre_2fa".equals(claims.get("scope", String.class))) {
            return ResponseEntity.status(403).body(Map.of("message", "Invalid token scope."));
        }

        Long userId = Long.parseLong(claims.getSubject());
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found."));
        }

        boolean valid = totpService.verifyAndConsumeBackupCode(userId, backupCode);
        if (!valid) {
            logger.warn("SECURITY [2fa]: Invalid backup code for user: {}", user.getEmail());
            auditService.logEvent(
                    null,
                    AuditService.TOTP_VERIFY_FAILED,
                    "USER",
                    null,
                    Map.of("email", user.getEmail(), "method", "backup_code"),
                    clientIp,
                    request.getHeader("User-Agent")
            );
            return ResponseEntity.status(401).body(Map.of("message", "Invalid backup code."));
        }

        user.setTotpVerifiedAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user);
        logger.info("SECURITY [2fa]: Backup code used — full JWT issued for user: {}", user.getEmail());

        return ResponseEntity.ok(Map.of("accessToken", accessToken));
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isEmpty()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }

}
