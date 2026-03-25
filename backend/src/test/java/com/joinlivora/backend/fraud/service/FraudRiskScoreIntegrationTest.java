package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.model.FraudRiskScore;
import com.joinlivora.backend.fraud.repository.FraudRiskScoreRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FraudRiskScoreIntegrationTest {

    @Autowired
    private FraudRiskScoreService fraudRiskScoreService;

    @Autowired
    private FraudRiskScoreRepository fraudRiskScoreRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testCalculateAndSaveScore() {
        User user = new User();
        user.setEmail("fraud-test@test.com");
        user.setUsername("fraud-test");
        user.setPassword("pass");
        user.setRole(com.joinlivora.backend.user.Role.USER);
        user = userRepository.save(user);

        Map<String, Object> context = new HashMap<>();
        context.put("country", "US");
        context.put("deviceFingerprint", "unique-fp");

        int score = fraudRiskScoreService.calculateAndSaveScore(user, context);

        assertTrue(score >= 0 && score <= 100);

        Optional<FraudRiskScore> saved = fraudRiskScoreRepository.findById(user.getId());
        assertTrue(saved.isPresent());
        assertEquals(score, saved.get().getScore());
        assertNotNull(saved.get().getFactors());
        assertTrue(saved.get().getFactors().containsKey("ACCOUNT_AGE"));
    }
}








