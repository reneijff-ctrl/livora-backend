package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreatorOnboardingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.joinlivora.backend.payout.LegacyCreatorProfileRepository legacyCreatorProfileRepository;

    private User creatorUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        legacyCreatorProfileRepository.deleteAll();
        creatorProfileRepository.deleteAll();
        userRepository.deleteAll();

        creatorUser = new User();
        creatorUser.setEmail("creator@test.com");
        creatorUser.setUsername("creator");
        creatorUser.setPassword("password");
        creatorUser.setRole(Role.CREATOR);
        creatorUser = userRepository.save(creatorUser);

        regularUser = new User();
        regularUser.setEmail("user@test.com");
        regularUser.setUsername("user");
        regularUser.setPassword("password");
        regularUser.setRole(Role.USER);
        regularUser = userRepository.save(regularUser);
    }

    @Test
    void upgradeAndAuth_AsUser_ShouldReturnOk() throws Exception {
        String token = jwtService.generateAccessToken(regularUser);

        mockMvc.perform(post("/api/creator/onboarding/upgrade-and-auth")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        
        User updatedUser = userRepository.findById(regularUser.getId()).orElseThrow();
        assertEquals(Role.CREATOR, updatedUser.getRole());
        assertTrue(creatorProfileRepository.findByUser(updatedUser).isPresent());
    }

    @Test
    void upgradeAndAuth_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/creator/onboarding/upgrade-and-auth")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}








