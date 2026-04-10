package com.joinlivora.backend.featureflag;

import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagService {

    private final FeatureFlagRepository repository;

    /**
     * Evaluates a feature flag for a specific creator.
     * 
     * @param key The feature flag key.
     * @param user The creator to evaluate the flag for (nullable).
     * @return true if the feature is enabled for the creator, false otherwise.
     */
    public boolean isEnabled(String key, User user) {
        try {
            FeatureFlag flag = getFlag(key);
            if (flag == null) {
                return false; // Default = OFF
            }

            if (!flag.isEnabled()) {
                return false; // Global OFF
            }

            // Role-based override: ADMIN always enabled
            if (user != null && user.getRole() == Role.ADMIN) {
                return true;
            }

            if (flag.getRolloutPercentage() >= 100) {
                return true; // Global ON
            }

            if (flag.getRolloutPercentage() <= 0) {
                return false; // Percentage OFF
            }

            if (user == null) {
                return false; // Cannot evaluate percentage rollout for anonymous users (conservative)
            }

            // Deterministic percentage rollout
            return isUserInRollout(user.getId().toString(), key, flag.getRolloutPercentage());

        } catch (Exception e) {
            log.error("Error evaluating feature flag: {}. Failing safe (OFF).", key, e);
            return false;
        }
    }

    @Cacheable(value = "feature_flags", key = "#key", unless = "#result == null")
    public FeatureFlag getFlag(String key) {
        return repository.findByKey(key).orElse(null);
    }

    @CacheEvict(value = "feature_flags", key = "#key")
    public void evictCache(String key) {
        log.info("Evicting feature flag cache for: {}", key);
    }

    @Scheduled(fixedRate = 60000) // Refresh cache every 60 seconds
    @CacheEvict(value = "feature_flags", allEntries = true)
    public void refreshAllFlags() {
        log.debug("Refreshing all feature flags cache");
    }

    private boolean isUserInRollout(String userId, String flagKey, int percentage) {
        try {
            String combined = userId + ":" + flagKey;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            
            // Use the first 4 bytes to get a stable integer
            int value = ((hash[0] & 0xFF) << 24) |
                        ((hash[1] & 0xFF) << 16) |
                        ((hash[2] & 0xFF) << 8) |
                        (hash[3] & 0xFF);
            
            int bucket = Math.abs(value) % 100;
            return bucket < percentage;
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            return false;
        }
    }

    public FeatureFlag saveFlag(FeatureFlag flag) {
        FeatureFlag saved = repository.save(flag);
        evictCache(saved.getKey());
        return saved;
    }

    public void deleteFlag(String key) {
        repository.findByKey(key).ifPresent(flag -> {
            repository.delete(flag);
            evictCache(key);
        });
    }
}
