package com.joinlivora.backend.controller;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SecurityAnalysisTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;

    private User creatorUser;
    private String creatorToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        creatorUser = new User();
        creatorUser.setEmail("creator@test.com");
        creatorUser.setUsername("creator");
        creatorUser.setPassword("password");
        creatorUser.setRole(Role.CREATOR);
        creatorUser = userRepository.save(creatorUser);

        creatorToken = jwtService.generateAccessToken(creatorUser);
        
        // Ensure profile exists
        creatorProfileService.initializeCreatorProfile(creatorUser);
    }

    @Test
    void creatorCanReadProfile() throws Exception {
        mockMvc.perform(get("/api/creator/profile")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk());
    }

    @Test
    void creatorCanUpdateProfile() throws Exception {
        String json = "{\"displayName\": \"New Name\", \"bio\": \"New Bio\"}";
        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void creatorIsBlockedFromUserProfile() throws Exception {
        mockMvc.perform(post("/api/user/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isForbidden());
    }
}








