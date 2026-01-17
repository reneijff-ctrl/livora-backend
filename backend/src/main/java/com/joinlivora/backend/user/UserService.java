package com.joinlivora.backend.user;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        return userRepository.save(user);
    }

    // =========================
    // SUPPORT
    // =========================
    @Cacheable(value = "users", key = "#email")
    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    // =========================
    // UPGRADE ROLE
    // =========================
    @CacheEvict(value = "users", key = "#email")
    public void upgradeToPremium(String email) {
        User user = getByEmail(email);
        user.setRole(Role.PREMIUM);
        userRepository.save(user);
    }

    @CacheEvict(value = "users", key = "#email")
    public void downgradeToUser(String email) {
        User user = getByEmail(email);
        user.setRole(Role.USER);
        userRepository.save(user);
    }

    public void incrementFailedAttempts(User user) {
        int newAttempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(newAttempts);
        if (newAttempts >= 5) {
            user.setLockoutUntil(java.time.Instant.now().plus(java.time.Duration.ofMinutes(10)));
        }
        userRepository.save(user);
    }

    public void resetFailedAttempts(String email) {
        User user = getByEmail(email);
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        userRepository.save(user);
    }
}
