package com.joinlivora.backend.user;

import com.joinlivora.backend.testutil.TestUserFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void testSaveUserWithFraudRiskLevel() {
        User user = TestUserFactory.createViewer("test@example.com");
        user.setFraudRiskLevel(FraudRiskLevel.MEDIUM);
        
        User saved = userRepository.save(user);
        assertNotNull(saved.getId());
        
        User found = userRepository.findById(saved.getId()).orElseThrow();
        assertEquals(FraudRiskLevel.MEDIUM, found.getFraudRiskLevel());
    }

    @Test
    void testDefaultFraudRiskLevel() {
        User user = TestUserFactory.createViewer("default@example.com");
        User saved = userRepository.save(user);
        
        User found = userRepository.findById(saved.getId()).orElseThrow();
        assertEquals(FraudRiskLevel.LOW, found.getFraudRiskLevel());
    }
}








