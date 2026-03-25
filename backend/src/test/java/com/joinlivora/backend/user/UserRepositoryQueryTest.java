package com.joinlivora.backend.user;

import com.joinlivora.backend.payout.LegacyCreatorProfile;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
import com.joinlivora.backend.testutil.TestUserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryQueryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LegacyCreatorProfileRepository legacyCreatorProfileRepository;

    private User creator;
    private User regularUser;

    @BeforeEach
    void setUp() {
        legacyCreatorProfileRepository.deleteAll();
        userRepository.deleteAll();

        creator = TestUserFactory.createCreator("creator@test.com");
        creator = userRepository.save(creator);

        LegacyCreatorProfile profile = LegacyCreatorProfile.builder()
                .user(creator)
                .username("creator_user")
                .displayName("Creator Display")
                .bio("Bio")
                .active(true)
                .category("General")
                .build();
        legacyCreatorProfileRepository.save(profile);

        regularUser = TestUserFactory.createViewer("user@test.com");
        regularUser = userRepository.save(regularUser);
    }

    @Test
    void findCreatorByIdOrUsername_ById_ShouldReturnCreator() {
        Optional<User> result = userRepository.findCreatorByIdOrUsername(creator.getId(), null);
        assertTrue(result.isPresent());
        assertEquals(creator.getId(), result.get().getId());
    }

    @Test
    void findCreatorByIdOrUsername_ByUsername_ShouldReturnCreator() {
        Optional<User> result = userRepository.findCreatorByIdOrUsername(null, "creator_user");
        assertTrue(result.isPresent());
        assertEquals(creator.getId(), result.get().getId());
    }

    @Test
    void findCreatorByIdOrUsername_ById_NonCreator_ShouldReturnEmpty() {
        Optional<User> result = userRepository.findCreatorByIdOrUsername(regularUser.getId(), null);
        assertTrue(result.isEmpty());
    }

    @Test
    void findCreatorByIdOrUsername_ByNonExistentId_ShouldReturnEmpty() {
        Optional<User> result = userRepository.findCreatorByIdOrUsername(999L, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void findCreatorByIdOrUsername_ByNonExistentUsername_ShouldReturnEmpty() {
        Optional<User> result = userRepository.findCreatorByIdOrUsername(null, "non_existent");
        assertTrue(result.isEmpty());
    }

    @Test
    void findCreatorByIdOrUsername_ByUsername_ButNotCreatorRole_ShouldReturnEmpty() {
        // Change role to USER but keep profile (edge case)
        creator.setRole(Role.USER);
        userRepository.save(creator);

        Optional<User> result = userRepository.findCreatorByIdOrUsername(null, "creator_user");
        assertTrue(result.isEmpty());
    }
}








