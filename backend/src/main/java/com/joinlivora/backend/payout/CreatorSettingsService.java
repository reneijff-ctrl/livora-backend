package com.joinlivora.backend.payout;

import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.payout.dto.CreatorSettingsDto;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatorSettingsService {

    private final LegacyCreatorProfileRepository legacyCreatorProfileRepository;
    private final CreatorProfileRepository creatorProfileRepository;

    @Transactional(readOnly = true)
    public CreatorSettingsDto getSettings(User user) {
        // Only role CREATOR or ADMIN may access settings
        if (user.getRole() != Role.CREATOR && user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only users with role CREATOR or ADMIN may access settings");
        }

        LegacyCreatorProfile profile = legacyCreatorProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + user.getEmail()));

        return CreatorSettingsDto.builder()
                .username(profile.getUsername())
                .category(profile.getCategory())
                .active(profile.isActive())
                .build();
    }

    @Transactional
    public CreatorSettingsDto updateSettings(User user, CreatorSettingsDto settingsDto) {
        // Requirement: Only role ADMIN may update private settings
        if (user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only users with role ADMIN may update private settings");
        }

        LegacyCreatorProfile legacyProfile = legacyCreatorProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + user.getEmail()));

        // Update legacy profile (only if fields are provided)
        if (settingsDto.getUsername() != null) {
            legacyProfile.setUsername(settingsDto.getUsername().trim());
        }
        if (settingsDto.getCategory() != null) {
            legacyProfile.setCategory(settingsDto.getCategory().trim());
        }
        legacyProfile.setActive(settingsDto.isActive());

        LegacyCreatorProfile saved = legacyCreatorProfileRepository.save(legacyProfile);

        // Synchronize with the new CreatorProfile entity
        creatorProfileRepository.findByUser(user).ifPresent(profile -> {
            if (settingsDto.getUsername() != null) {
                profile.setUsername(settingsDto.getUsername().trim());
            }
            creatorProfileRepository.save(profile);
        });

        return CreatorSettingsDto.builder()
                .username(saved.getUsername())
                .category(saved.getCategory())
                .active(saved.isActive())
                .build();
    }
}
