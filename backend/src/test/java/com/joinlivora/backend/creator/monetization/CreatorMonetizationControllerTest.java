package com.joinlivora.backend.creator.monetization;

import com.joinlivora.backend.creator.model.CreatorMonetization;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.testutil.TestUserFactory;
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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreatorMonetizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private CreatorMonetizationRepository creatorMonetizationRepository;

    @Autowired
    private com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = TestUserFactory.createCreator("creator-mon@test.com");
        creator = userRepository.save(creator);
        creatorProfileService.initializeCreatorProfile(creator);
    }

    @Test
    void getMonetization_ShouldReturnDefaultValues() throws Exception {
        String token = jwtService.generateAccessToken(creator);

        mockMvc.perform(get("/api/creator/monetization/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionPrice").value(9.99))
                .andExpect(jsonPath("$.tipEnabled").value(true))
                .andExpect(jsonPath("$.balance").exists())
                .andExpect(jsonPath("$.pendingBalance").exists())
                .andExpect(jsonPath("$.lifetimeEarnings").exists());
    }

    @Test
    void getMonetization_WithExistingRecord_ShouldReturnValues() throws Exception {
        CreatorProfile profile = creatorProfileRepository.findByUser(creator).get();
        CreatorMonetization monetization = creatorMonetizationRepository.findByCreator(profile).get();
        monetization.setSubscriptionPrice(new BigDecimal("14.99"));
        monetization.setTipEnabled(false);
        creatorMonetizationRepository.save(monetization);

        String token = jwtService.generateAccessToken(creator);

        mockMvc.perform(get("/api/creator/monetization/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionPrice").value(14.99))
                .andExpect(jsonPath("$.tipEnabled").value(false))
                .andExpect(jsonPath("$.balance").exists())
                .andExpect(jsonPath("$.pendingBalance").exists())
                .andExpect(jsonPath("$.lifetimeEarnings").exists());
    }

    @Test
    void getMonetization_AsUser_ShouldReturn403() throws Exception {
        User user = TestUserFactory.createViewer("user-mon@test.com");
        user = userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/creator/monetization/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMonetization_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/creator/monetization/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}








