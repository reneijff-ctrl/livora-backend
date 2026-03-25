package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.model.CreatorMonetization;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.monetization.CreatorMonetizationRepository;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class CreatorMonetizationAutoCreateTest {

    @Autowired
    private CreatorProfileService creatorProfileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorMonetizationRepository monetizationRepository;

    @Test
    void getCreatorByUserId_ShouldAutoCreateMonetization() {
        // Given
        User user = TestUserFactory.createCreator("monetized@example.com");
        user = userRepository.save(user);
        Long userId = user.getId();

        // When
        CreatorProfile profile = creatorProfileService.getCreatorByUserId(userId);

        // Then
        Optional<CreatorMonetization> monetizationOpt = monetizationRepository.findByCreator(profile);
        assertTrue(monetizationOpt.isPresent(), "Monetization record should be created");
        CreatorMonetization monetization = monetizationOpt.get();
        assertEquals(0, new BigDecimal("9.99").compareTo(monetization.getSubscriptionPrice()));
        assertTrue(monetization.isTipEnabled());
        assertNotNull(monetization.getCreatedAt());
        assertNotNull(monetization.getUpdatedAt());
    }

    @Test
    void initializeCreatorProfile_ShouldCreateMonetizationIfMissing() {
        // Given
        User user = TestUserFactory.createCreator("ensure_monetized@example.com");
        user = userRepository.save(user);
        CreatorProfile profile = creatorProfileService.getCreatorByUserId(user.getId());
        
        // Manually delete monetization record to simulate missing one
        monetizationRepository.delete(monetizationRepository.findByCreator(profile).get());
        monetizationRepository.flush();
        assertTrue(monetizationRepository.findByCreator(profile).isEmpty());

        // When
        creatorProfileService.initializeCreatorProfile(user);

        // Then
        assertTrue(monetizationRepository.findByCreator(profile).isPresent(), "Monetization record should be re-created");
    }
}








