package com.joinlivora.backend.user;

import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.payout.LegacyCreatorProfile;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
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
class RoleUpgradeIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private LegacyCreatorProfileRepository legacyCreatorProfileRepository;

    @Test
    void upgradeToCreator_ShouldCreateProfilesAndBeIdempotent() {
        // 1. Create a regular user
        String email = "upgrade_test@example.com";
        User user = userService.registerUser(email, "password123");
        assertEquals(Role.USER, user.getRole());

        // 2. Upgrade to creator
        userService.upgradeToCreator(email);

        // 3. Verify role changed
        User upgradedUser = userRepository.findByEmail(email).orElseThrow();
        assertEquals(Role.CREATOR, upgradedUser.getRole());

        // 4. Verify CreatorProfile created
        Optional<CreatorProfile> profileOpt = creatorProfileRepository.findByUser(upgradedUser);
        assertTrue(profileOpt.isPresent(), "CreatorProfile should be created");
        CreatorProfile profile = profileOpt.get();
        assertNotNull(profile.getUsername());
        assertNotNull(profile.getPublicHandle(), "publicHandle (slug) should be generated");
        assertEquals(profile.getUsername(), profile.getPublicHandle());

        // 5. Verify LegacyCreatorProfile created
        Optional<LegacyCreatorProfile> legacyOpt = legacyCreatorProfileRepository.findByUser(upgradedUser);
        assertTrue(legacyOpt.isPresent(), "LegacyCreatorProfile should be created");

        // 6. Test Idempotency: Call again
        userService.upgradeToCreator(email);

        // 7. Verify no duplicate profiles and still correct
        assertEquals(1, creatorProfileRepository.countByUserId(upgradedUser.getId()));
        assertEquals(Role.CREATOR, userRepository.findByEmail(email).get().getRole());
        
        // 8. Test partial failure recovery: Delete profile but keep role, then run upgrade
        creatorProfileRepository.delete(profile);
        assertEquals(0, creatorProfileRepository.countByUserId(upgradedUser.getId()));
        
        userService.upgradeToCreator(email);
        
        assertTrue(creatorProfileRepository.findByUser(upgradedUser).isPresent(), 
                "Should recreate profile if role is already CREATOR but profile is missing");
    }
}








