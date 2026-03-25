package com.joinlivora.backend.security;

import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock
    private CreatorProfileRepository creatorProfileRepository;

    @InjectMocks
    private SecurityService securityService;

    @Test
    void isActiveCreator_ShouldReturnTrue_WhenProfileIsActive() {
        CreatorProfile profile = new CreatorProfile();
        profile.setStatus(ProfileStatus.ACTIVE);
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        assertTrue(securityService.isActiveCreator(1L));
    }

    @Test
    void isActiveCreator_ShouldReturnFalse_WhenProfileIsDraft() {
        CreatorProfile profile = new CreatorProfile();
        profile.setStatus(ProfileStatus.DRAFT);
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        assertFalse(securityService.isActiveCreator(1L));
    }

    @Test
    void isActiveCreator_ShouldReturnFalse_WhenProfileIsSuspended() {
        CreatorProfile profile = new CreatorProfile();
        profile.setStatus(ProfileStatus.SUSPENDED);
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        assertFalse(securityService.isActiveCreator(1L));
    }

    @Test
    void isActiveCreator_ShouldReturnFalse_WhenProfileMissing() {
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertFalse(securityService.isActiveCreator(1L));
    }
}








