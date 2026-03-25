package com.joinlivora.backend.user;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.payout.LegacyCreatorProfile;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private LegacyCreatorProfileRepository creatorProfileRepository;
    @Mock
    private com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setRole(Role.USER);
    }

    @Test
    void upgradeToCreator_ShouldChangeRoleAndCreateProfile() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.empty());

        userService.upgradeToCreator("test@example.com");

        assertEquals(Role.CREATOR, user.getRole());
        verify(userRepository).save(user);
        
        ArgumentCaptor<LegacyCreatorProfile> profileCaptor = ArgumentCaptor.forClass(LegacyCreatorProfile.class);
        verify(creatorProfileRepository).save(profileCaptor.capture());
        
        LegacyCreatorProfile savedProfile = profileCaptor.getValue();
        assertEquals(user, savedProfile.getUser());
        assertEquals("test", savedProfile.getDisplayName());
        assertEquals("", savedProfile.getBio());
        assertTrue(savedProfile.isActive());
        assertEquals("General", savedProfile.getCategory());

        verify(auditService).logEvent(
                isNull(),
                eq(AuditService.ROLE_CHANGED),
                eq("USER"),
                eq(new UUID(0L, 1L)),
                any(),
                isNull(),
                isNull()
        );
    }

    @Test
    void upgradeToCreator_WhenAlreadyCreator_ShouldDoNothing() {
        user.setRole(Role.CREATOR);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(new LegacyCreatorProfile()));

        userService.upgradeToCreator("test@example.com");

        assertEquals(Role.CREATOR, user.getRole());
        verify(userRepository, never()).save(any());
        verify(creatorProfileRepository, never()).save(any());
    }

    @Test
    void upgradeToCreator_WhenAdmin_ShouldDoNothing() {
        user.setRole(Role.ADMIN);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        userService.upgradeToCreator("test@example.com");

        assertEquals(Role.ADMIN, user.getRole());
        verify(userRepository, never()).save(any());
        verify(creatorProfileRepository, never()).save(any());
    }

    @Test
    void getUserMe_ShouldReturnUserResponse() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        com.joinlivora.backend.user.dto.UserResponse result = userService.getUserMe("test@example.com");

        assertNotNull(result);
        assertEquals(user.getId(), result.getId());
        assertEquals(user.getEmail(), result.getEmail());
        assertEquals(user.getRole(), result.getRole());
    }
}








