package com.joinlivora.backend.user;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.payout.LegacyCreatorProfile;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final LegacyCreatorProfileRepository legacyCreatorProfileRepository;
    private final CreatorProfileService creatorProfileService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            LegacyCreatorProfileRepository legacyCreatorProfileRepository,
            CreatorProfileService creatorProfileService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.legacyCreatorProfileRepository = legacyCreatorProfileRepository;
        this.creatorProfileService = creatorProfileService;
    }

    // =========================
    // REGISTER
    // =========================
    public User registerUser(String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.USER);

        // Generate username from email prefix (simplified version of migration logic)
        String baseUsername = email.split("@")[0].toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (baseUsername.isBlank()) {
            baseUsername = "user";
        }
        if (baseUsername.length() > 30) {
            baseUsername = baseUsername.substring(0, 30);
        }

        String username = baseUsername;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            String suffix = "_" + counter;
            if (baseUsername.length() + suffix.length() > 30) {
                username = baseUsername.substring(0, 30 - suffix.length()) + suffix;
            } else {
                username = baseUsername + suffix;
            }
            counter++;
        }
        user.setUsername(username);

        return userRepository.save(user);
    }

    // =========================
    // SUPPORT
    // =========================
    // @Cacheable(value = "users", key = "#email", unless = "#result == null")
    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    public User findByEmail(String email) {
        return getByEmail(email);
    }

    public com.joinlivora.backend.user.dto.UserResponse getUserMe(String email) {
        User user = getByEmail(email);
        return new com.joinlivora.backend.user.dto.UserResponse(user.getId(), user.getEmail(), user.getRole());
    }

    /**
     * Resolves a User from a generic subject string (Email, numeric UserId, or Username).
     * This supports dual-mode parsing to ensure zero-downtime transition for tokens.
     */
    public java.util.Optional<User> resolveUserFromSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return java.util.Optional.empty();
        }

        // 1. Try Email (contains '@')
        if (subject.contains("@")) {
            return userRepository.findByEmail(subject);
        }

        // 2. Try Numeric ID
        try {
            Long userId = Long.parseLong(subject);
            return userRepository.findById(userId);
        } catch (NumberFormatException e) {
            // 3. Fallback: Try Username
            return userRepository.findByUsername(subject);
        }
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    public List<User> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return userRepository.findAllById(ids);
    }

    // =========================
    // UPGRADE ROLE
    // =========================
    @CacheEvict(value = "users", key = "#email")
    public void upgradeToPremium(String email) {
        User user = getByEmail(email);
        Role oldRole = user.getRole();
        user.setRole(Role.PREMIUM);
        userRepository.save(user);
        
        auditService.logEvent(
                null,
                AuditService.ROLE_CHANGED,
                "USER",
                new UUID(0L, user.getId()),
                Map.of("oldRole", oldRole, "newRole", Role.PREMIUM, "type", "Subscription upgrade"),
                null,
                null
        );
    }

    @Transactional
    @CacheEvict(value = "users", key = "#email")
    public void upgradeToCreator(String email) {
        User user = getByEmail(email);
        Role oldRole = user.getRole();

        if (user.getRole() != Role.CREATOR && user.getRole() != Role.ADMIN) {
            user.setRole(Role.CREATOR);
            userRepository.save(user);

            auditService.logEvent(
                    null,
                    AuditService.ROLE_CHANGED,
                    "USER",
                    new UUID(0L, user.getId()),
                    Map.of("oldRole", oldRole, "newRole", Role.CREATOR, "type", "Creator onboarding"),
                    null,
                    null
            );
        }

        // Always ensure profiles exist for creators, for idempotency and partial failure recovery
        if (user.getRole() == Role.CREATOR) {
            // Create LegacyCreatorProfile if it doesn't exist
            if (legacyCreatorProfileRepository.findByUser(user).isEmpty()) {
                String baseUsername = user.getUsername();
                LegacyCreatorProfile profile = LegacyCreatorProfile.builder()
                        .user(user)
                        .username(baseUsername)
                        .displayName(baseUsername)
                        .bio("")
                        .active(true)
                        .category("General")
                        .build();
                legacyCreatorProfileRepository.save(profile);
            }

            // Ensure new CreatorProfile also exists
            creatorProfileService.initializeCreatorProfile(user);
        }
    }

    @CacheEvict(value = "users", key = "#email")
    public void downgradeToUser(String email) {
        User user = getByEmail(email);
        Role oldRole = user.getRole();
        user.setRole(Role.USER);
        userRepository.save(user);

        auditService.logEvent(
                null,
                AuditService.ROLE_CHANGED,
                "USER",
                new UUID(0L, user.getId()),
                Map.of("oldRole", oldRole, "newRole", Role.USER, "type", "Subscription downgrade/expiration"),
                null,
                null
        );
    }

    public void incrementFailedAttempts(User user) {
        int newAttempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(newAttempts);
        if (newAttempts >= 5) {
            user.setLockoutUntil(Instant.now().plus(Duration.ofMinutes(10)));
        }
        userRepository.save(user);
    }

    public void resetFailedAttempts(String email) {
        User user = getByEmail(email);
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        userRepository.save(user);
    }

    @CacheEvict(value = "users", key = "#user.email")
    public void updateUser(User user) {
        userRepository.save(user);
    }

    /**
     * Updates the username for the given user after validation and uniqueness check.
     * Rules: 3-20 chars, letters/numbers/underscore/dot only, case-insensitive unique.
     */
    @CacheEvict(value = "users", key = "#user.email")
    public User updateUsername(User user, String newUsername) {
        if (newUsername == null || newUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        String trimmed = newUsername.trim();
        if (trimmed.length() < 3 || trimmed.length() > 20) {
            throw new IllegalArgumentException("Username must be between 3 and 20 characters");
        }
        if (!trimmed.matches("^[a-zA-Z0-9_.]+$")) {
            throw new IllegalArgumentException("Username can only contain letters, numbers, underscores, and dots");
        }
        // Skip update if unchanged
        if (trimmed.equals(user.getUsername())) {
            return user;
        }
        // Case-insensitive uniqueness check
        if (userRepository.existsByUsernameIgnoreCase(trimmed)) {
            throw new IllegalArgumentException("Username is already taken");
        }
        user.setUsername(trimmed);
        return userRepository.save(user);
    }

    // =========================
    // MODERATION
    // =========================
    @Transactional
    @CacheEvict(value = {"users", "publicContent"}, allEntries = true)
    public void suspendUser(Long userId, User admin, String reason, String ipAddress, String userAgent) {
        User user = getById(userId);
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        auditService.logEvent(
                admin != null ? new UUID(0L, admin.getId()) : null,
                AuditService.ACCOUNT_SUSPENDED,
                "USER",
                new UUID(0L, user.getId()),
                Map.of("type", reason != null ? reason : "Administrative suspension"),
                ipAddress,
                userAgent
        );
    }

    @Transactional
    @CacheEvict(value = {"users", "publicContent"}, allEntries = true)
    public void unsuspendUser(Long userId, User admin, String reason, String ipAddress, String userAgent) {
        User user = getById(userId);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        auditService.logEvent(
                admin != null ? new UUID(0L, admin.getId()) : null,
                AuditService.ACCOUNT_UNSUSPENDED,
                "USER",
                new UUID(0L, user.getId()),
                Map.of("type", reason != null ? reason : "Administrative unsuspension"),
                ipAddress,
                userAgent
        );
    }

    @Transactional
    @CacheEvict(value = {"users", "publicContent"}, allEntries = true)
    public void shadowbanCreator(UUID profileId, User admin, String reason, String ipAddress, String userAgent) {
        LegacyCreatorProfile profile = legacyCreatorProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found: " + profileId));
        
        User user = profile.getUser();
        user.setShadowbanned(true);
        userRepository.save(user);

        auditService.logEvent(
                admin != null ? new UUID(0L, admin.getId()) : null,
                AuditService.USER_SHADOWBANNED,
                "USER",
                new UUID(0L, user.getId()),
                Map.of("type", reason != null ? reason : "Administrative shadowban", "profileId", profileId),
                ipAddress,
                userAgent
        );
    }

    @Transactional(readOnly = true)
    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<User> getAllCreators(Pageable pageable) {
        return userRepository.findAllByRole(Role.CREATOR, pageable);
    }
}
