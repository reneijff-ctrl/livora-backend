package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.model.CreatorProfile;
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

import java.time.Instant;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreatorProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;

    @Autowired
    private com.joinlivora.backend.payout.LegacyCreatorProfileRepository legacyCreatorProfileRepository;

    private User creatorUser;
    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        legacyCreatorProfileRepository.deleteAll();
        creatorProfileRepository.deleteAll();
        userRepository.deleteAll();

        creatorUser = new User();
        creatorUser.setEmail("creator-profile@test.com");
        creatorUser.setPassword("password");
        creatorUser.setRole(Role.CREATOR);
        creatorUser.setUsername("creator-profile");
        creatorUser = userRepository.save(creatorUser);

        adminUser = new User();
        adminUser.setEmail("admin-profile@test.com");
        adminUser.setPassword("password");
        adminUser.setRole(Role.ADMIN);
        adminUser.setUsername("admin-profile");
        adminUser = userRepository.save(adminUser);

        regularUser = new User();
        regularUser.setEmail("user-profile@test.com");
        regularUser.setPassword("password");
        regularUser.setRole(Role.USER);
        regularUser.setUsername("user-profile");
        regularUser = userRepository.save(regularUser);

        // Pre-create profile for creatorUser to avoid 403 on update tests
        creatorProfileService.initializeCreatorProfile(creatorUser);
    }

    @Test
    void getProfile_AsCreator_ShouldSucceedAndAutoCreate() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);

        mockMvc.perform(get("/api/creator/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("creator-profile"))
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()));

        assertTrue(creatorProfileRepository.findByUser(creatorUser).isPresent());
    }

    @Test
    void getProfile_AsAdmin_ShouldReturn404() throws Exception {
        String token = jwtService.generateAccessToken(adminUser);
        
        // Create profile should fail for Admin
        mockMvc.perform(post("/api/creator/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Get profile should also fail
        mockMvc.perform(get("/api/creator/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProfile_AsCreator_ShouldReturnForbidden() throws Exception {
        User newCreator = new User();
        newCreator.setEmail("new-creator@test.com");
        newCreator.setPassword("password");
        newCreator.setRole(Role.CREATOR);
        newCreator.setUsername("new-creator");
        newCreator = userRepository.save(newCreator);
        
        String token = jwtService.generateAccessToken(newCreator);

        mockMvc.perform(post("/api/creator/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProfile_AsRegularUser_ShouldReturn403() throws Exception {
        String token = jwtService.generateAccessToken(regularUser);

        mockMvc.perform(get("/api/creator/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProfile_WhenAlreadyExists_ShouldReturnExisting() throws Exception {
        CreatorProfile profile = creatorProfileRepository.findByUser(creatorUser).orElseThrow();
        profile.setDisplayName("Custom Name");
        profile.setBio("My bio");
        creatorProfileRepository.save(profile);

        String token = jwtService.generateAccessToken(creatorUser);

        mockMvc.perform(get("/api/creator/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Custom Name"))
                .andExpect(jsonPath("$.bio").value("My bio"));
    }

    @Test
    void getProfile_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/creator/profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_AsCreator_ShouldSucceed() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        String json = "{\"displayName\": \"Updated Name\"}";

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Name"));

        CreatorProfile profile = creatorProfileRepository.findByUser(creatorUser).orElseThrow();
        assertEquals("Updated Name", profile.getDisplayName());
    }

    @Test
    void updateProfile_AsAdmin_ShouldFailAtCreation() throws Exception {
        String token = jwtService.generateAccessToken(adminUser);
        
        // Creation should fail for Admin
        mockMvc.perform(post("/api/creator/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProfile_AsRegularUser_ShouldReturn403() throws Exception {
        String token = jwtService.generateAccessToken(regularUser);
        String json = "{\"displayName\": \"User Update\"}";

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProfile_ShouldIgnoreCreatedAt() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        // "createdAt" should be ignored
        String json = "{\"displayName\": \"Valid Name\", \"createdAt\": \"2020-01-01T00:00:00Z\"}";

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void updateProfile_DisplayNameTooShort_ShouldReturn400() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        String json = "{\"displayName\": \"A\"}";

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_DisplayNameTooLong_ShouldReturn400() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        String longName = "a".repeat(51);
        String json = String.format("{\"displayName\": \"%s\"}", longName);

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_DisplayNameBlank_ShouldReturn400() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        String json = "{\"displayName\": \"   \"}";

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_BioTooLong_ShouldReturn400() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        String longBio = "a".repeat(501);
        String json = String.format("{\"displayName\": \"Valid Name\", \"bio\": \"%s\"}", longBio);

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_WithHtmlTags_ShouldReturn400() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        
        // Test HTML in displayName
        String json1 = "{\"displayName\": \"<script>alert(1)</script>\"}";
        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json1))
                .andExpect(status().isBadRequest());

        // Test HTML in bio
        String json2 = "{\"displayName\": \"Valid Name\", \"bio\": \"<b>Bold</b>\"}";
        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json2))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_ShouldSucceedWithWhitespace() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        String json = "{\"displayName\": \"  Trimmed Name  \"}";

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Trimmed Name"));
    }

    @Test
    void updateProfile_WhenProfileMissing_ShouldReturn404() throws Exception {
        // Create a new creator but DELETE their auto-created profile if any
        User newCreator = new User();
        newCreator.setEmail("no-profile@test.com");
        newCreator.setPassword("password");
        newCreator.setRole(Role.CREATOR);
        newCreator.setUsername("no-profile");
        newCreator = userRepository.save(newCreator);
        
        // Ensure NO profile exists
        creatorProfileRepository.deleteByUser(newCreator);

        String token = jwtService.generateAccessToken(newCreator);
        String json = "{\"displayName\": \"Name\"}";

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProfile_AsCreator_ShouldSucceed() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        
        // Update CreatorProfile with specific username
        CreatorProfile profile = creatorProfileRepository.findByUser(creatorUser).orElseThrow();
        profile.setUsername("creator_user");
        creatorProfileRepository.save(profile);

        mockMvc.perform(get("/api/creator/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("creator-profile"))
                .andExpect(jsonPath("$.username").value("creator_user"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void getProfile_WhenProfileMissing_ShouldReturn404() throws Exception {
        User newCreator = new User();
        newCreator.setEmail("no-profile-me@test.com");
        newCreator.setPassword("password");
        newCreator.setRole(Role.CREATOR);
        newCreator.setUsername("no-profile-me");
        newCreator = userRepository.save(newCreator);
        
        creatorProfileRepository.deleteByUser(newCreator);

        String token = jwtService.generateAccessToken(newCreator);

        mockMvc.perform(get("/api/creator/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
    @Test
    void publishProfile_AsCreator_ShouldReturnForbidden() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        
        mockMvc.perform(post("/api/creator/profile/publish")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}








