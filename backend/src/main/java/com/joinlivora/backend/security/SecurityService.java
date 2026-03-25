package com.joinlivora.backend.security;

import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("securityService")
@RequiredArgsConstructor
public class SecurityService {

    private final CreatorProfileRepository creatorProfileRepository;

    public boolean isActiveCreator(Long userId) {
        return creatorProfileRepository.findByUserId(userId)
                .map(profile -> profile.getStatus() == ProfileStatus.ACTIVE)
                .orElse(false);
    }
}
