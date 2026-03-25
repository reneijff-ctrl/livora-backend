package com.joinlivora.backend.creator.controller;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreatorDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;

    @Autowired
    private com.joinlivora.backend.creator.repository.CreatorProfileRepository creatorProfileRepository;

    private User creator;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        creator = TestUserFactory.createCreator("creator@test.com");
        creator = userRepository.save(creator);
        creatorProfileService.initializeCreatorProfile(creator);
    }

    @Test
    void getDashboard_ShouldReturnDto() throws Exception {
        String token = jwtService.generateAccessToken(creator);

        mockMvc.perform(get("/api/creator/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorProfile.username").value("creator"))
                .andExpect(jsonPath("$.creatorProfile.displayName").value("creator"))
                .andExpect(jsonPath("$.stats.totalEarnings").value(0))
                .andExpect(jsonPath("$.stats.totalFollowers").value(0))
                .andExpect(jsonPath("$.stats.verified").value(false))
                .andExpect(jsonPath("$.stats.availableBalance").value(0))
                .andExpect(jsonPath("$.stats.activeStreams").value(0))
                .andExpect(jsonPath("$.stats.contentCount").value(0))
                .andExpect(jsonPath("$.stats.status").value("ACTIVE"));
    }

    @Test
    void getDashboard_AsUser_ShouldReturn403() throws Exception {
        User user = TestUserFactory.createViewer("user@test.com");
        user = userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/creator/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDashboard_AsAdmin_ShouldReturn404() throws Exception {
        User adminUser = TestUserFactory.createUser("admin-dashboard@test.com", Role.ADMIN);
        adminUser = userRepository.save(adminUser);
        String token = jwtService.generateAccessToken(adminUser);

        mockMvc.perform(get("/api/creator/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDashboard_AsPremium_ShouldReturn403() throws Exception {
        User premiumUser = TestUserFactory.createUser("premium-dashboard@test.com", Role.PREMIUM);
        premiumUser = userRepository.save(premiumUser);
        String token = jwtService.generateAccessToken(premiumUser);

        mockMvc.perform(get("/api/creator/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDashboard_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/creator/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSummary_AsAuthenticatedUser_ShouldSucceed() throws Exception {
        String token = jwtService.generateAccessToken(creator);
        Long profileId = creatorProfileRepository.findByUser(creator).get().getId();

        mockMvc.perform(get("/api/creator/dashboard/summary")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profileId))
                .andExpect(jsonPath("$.displayName").value("creator"))
                .andExpect(jsonPath("$.totalEarnings").value(0))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.profileStatus").value("ACTIVE"));
    }

    @Test
    void getSummary_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/creator/dashboard/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getStats_ShouldReturnStats() throws Exception {
        String token = jwtService.generateAccessToken(creator);

        mockMvc.perform(get("/api/creator/dashboard/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNetEarnings").exists());
    }

    @Test
    void getEarnings_ShouldReturnHistory() throws Exception {
        String token = jwtService.generateAccessToken(creator);

        mockMvc.perform(get("/api/creator/dashboard/earnings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getStatistics_ShouldReturnStatistics() throws Exception {
        String token = jwtService.generateAccessToken(creator);

        mockMvc.perform(get("/api/creator/dashboard/statistics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postsCount").value(0))
                .andExpect(jsonPath("$.tipsCount").value(0))
                .andExpect(jsonPath("$.subscribersCount").value(0));
    }

    @Test
    void getStatistics_AsAdmin_ShouldReturn403() throws Exception {
        User adminUser = TestUserFactory.createUser("admin-stats@test.com", Role.ADMIN);
        adminUser = userRepository.save(adminUser);
        String token = jwtService.generateAccessToken(adminUser);

        mockMvc.perform(get("/api/creator/dashboard/statistics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}








