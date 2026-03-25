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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreatorControllerMeStatsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.creator.service.OnlineStatusService onlineStatusService;

    private User creator;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        creator = TestUserFactory.createCreator("creator-stats@test.com");
        creator = userRepository.save(creator);
    }

    @Test
    void getMyStats_AsCreator_ShouldReturnStats() throws Exception {
        String token = jwtService.generateAccessToken(creator);

        mockMvc.perform(get("/api/creators/me/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPosts").value(0))
                .andExpect(jsonPath("$.totalEarnings").value(0.0))
                .andExpect(jsonPath("$.pendingBalance").value(0.0));
    }

    @Test
    void getMyStats_AsUser_ShouldReturn403() throws Exception {
        User user = TestUserFactory.createViewer("user@test.com");
        user = userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/creators/me/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyStats_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/creators/me/stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}








