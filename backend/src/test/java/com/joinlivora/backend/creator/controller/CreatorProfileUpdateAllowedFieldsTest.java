package com.joinlivora.backend.creator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.creator.dto.UpdateCreatorProfileRequest;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CreatorProfileUpdateAllowedFieldsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private CreatorProfileService creatorProfileService;

    @Autowired
    private com.joinlivora.backend.security.JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private User creator;
    private String token;

    @BeforeEach
    void setUp() {
        creatorProfileRepository.deleteAll();
        userRepository.deleteAll();

        creator = new User();
        creator.setEmail("owner@test.com");
        creator.setUsername("owner");
        creator.setPassword("password");
        creator.setRole(Role.CREATOR);
        userRepository.save(creator);
        creatorProfileService.initializeCreatorProfile(creator);
        
        token = jwtService.generateAccessToken(creator);
    }

    @Test
    void updateProfile_DisplayNameOnly_ShouldSucceed() throws Exception {
        UpdateCreatorProfileRequest request = UpdateCreatorProfileRequest.builder()
                .displayName("New Name")
                .build();

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        CreatorProfile profile = creatorProfileRepository.findByUser(creator).orElseThrow();
        assertEquals("New Name", profile.getDisplayName());
    }

    @Test
    void updateProfile_IncludingBio_ShouldSucceed() throws Exception {
        UpdateCreatorProfileRequest request = UpdateCreatorProfileRequest.builder()
                .displayName("New Name")
                .bio("This should now be allowed")
                .build();

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        CreatorProfile profile = creatorProfileRepository.findByUser(creator).orElseThrow();
        assertEquals("This should now be allowed", profile.getBio());
    }

    @Test
    void updateProfile_IncludingAvatarUrl_ShouldSucceed() throws Exception {
        UpdateCreatorProfileRequest request = UpdateCreatorProfileRequest.builder()
                .displayName("New Name")
                .profileImageUrl("http://example.com/avatar.jpg")
                .build();

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        CreatorProfile profile = creatorProfileRepository.findByUser(creator).orElseThrow();
        assertEquals("http://example.com/avatar.jpg", profile.getAvatarUrl());
    }

    @Test
    void updateProfile_AsAdmin_ShouldReturn403() throws Exception {
        // Create an Admin user
        User admin = new User();
        String adminSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        admin.setEmail("admin_" + adminSuffix + "@test.com");
        admin.setUsername("admin_" + adminSuffix);
        admin.setPassword("password");
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        String adminToken = jwtService.generateAccessToken(admin);
        
        UpdateCreatorProfileRequest request = UpdateCreatorProfileRequest.builder()
                .displayName("Admin Name")
                .build();

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}








