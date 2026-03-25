package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class AutoCreateCreatorProfileTest {

    @Autowired
    private CreatorProfileService creatorProfileService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void getCreatorByUserId_ShouldAutoCreateIfMissingAndRoleIsCreator() {
        // Given a user with Role.CREATOR but no profile
        User user = TestUserFactory.createCreator("newcreator@example.com");
        user = userRepository.save(user);
        Long userId = user.getId();

        // When
        CreatorProfile profile = creatorProfileService.getCreatorByUserId(userId);

        // Then
        assertNotNull(profile);
        assertEquals(userId, profile.getUser().getId());
        assertEquals("newcreator", profile.getUsername());
        assertEquals("newcreator", profile.getDisplayName());
        assertEquals(ProfileStatus.ACTIVE, profile.getStatus());
    }

    @Test
    void getCreatorByUserId_ShouldNotAutoCreateIfRoleIsAdmin() {
        // Given a user with Role.ADMIN but no profile
        User user = TestUserFactory.createUser("admin@example.com", Role.ADMIN);
        user = userRepository.save(user);
        Long userId = user.getId();

        // When / Then
        assertThrows(com.joinlivora.backend.exception.ResourceNotFoundException.class, () -> {
            creatorProfileService.getCreatorByUserId(userId);
        });
    }

    @Test
    void getCreatorByUserId_ShouldNotAutoCreateIfRoleIsUser() {
        // Given a user with Role.USER but no profile
        User user = TestUserFactory.createViewer("regularuser@example.com");
        user = userRepository.save(user);
        Long userId = user.getId();

        // When / Then
        assertThrows(com.joinlivora.backend.exception.ResourceNotFoundException.class, () -> {
            creatorProfileService.getCreatorByUserId(userId);
        });
    }

    @Test
    void initializeCreatorProfile_ShouldThrowIfRoleIsAdmin() {
        // Given a user with Role.ADMIN but no profile
        final User user = userRepository.save(TestUserFactory.createUser("admin_ensure@example.com", Role.ADMIN));

        // When / Then
        assertThrows(com.joinlivora.backend.exception.ResourceNotFoundException.class, () -> {
            creatorProfileService.initializeCreatorProfile(user);
        });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProfile_ShouldThrowIfRoleIsAdmin() {
        // Given a user with Role.ADMIN
        User user = userRepository.save(TestUserFactory.createUser("admin_create@example.com", Role.ADMIN));
        final Long userId = user.getId();

        // When / Then
        assertThrows(com.joinlivora.backend.exception.ResourceNotFoundException.class, () -> {
            creatorProfileService.createProfile(userId);
        });
    }
}








