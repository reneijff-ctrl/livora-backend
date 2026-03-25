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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CreatorProfileOwnershipTest {

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

    @BeforeEach
    void setUp() {
        // Clear repositories to ensure a clean state
        creatorProfileRepository.deleteAll();
        userRepository.deleteAll();

        // Create Creator 1
        User c1 = new User();
        c1.setEmail("creator1@test.com");
        c1.setUsername("creator1");
        c1.setPassword("password");
        c1.setRole(Role.CREATOR);
        userRepository.save(c1);
        creatorProfileService.initializeCreatorProfile(c1);

        // Create Creator 2
        User c2 = new User();
        c2.setEmail("creator2@test.com");
        c2.setUsername("creator2");
        c2.setPassword("password");
        c2.setRole(Role.CREATOR);
        userRepository.save(c2);
        creatorProfileService.initializeCreatorProfile(c2);
    }

    /**
     * Scenario A: CREATOR updates their own profile -> 200 OK
     */
    @Test
    void updateProfile_OwnProfile_ShouldReturn200() throws Exception {
        User user = userRepository.findByEmail("creator1@test.com").orElseThrow();
        String token = jwtService.generateAccessToken(user);

        UpdateCreatorProfileRequest request = UpdateCreatorProfileRequest.builder()
                .displayName("Creator One Updated")
                .build();

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        
        CreatorProfile profile = creatorProfileRepository.findByUser(user).orElseThrow();
        assertEquals("Creator One Updated", profile.getDisplayName());
    }

    /**
     * Scenario B: CREATOR updates via their token -> Always updates their OWN profile
     * even if they intended otherwise (no ID parameter available anyway)
     */
    @Test
    void updateProfile_AlwaysUpdatesOwnProfile() throws Exception {
        User c1 = userRepository.findByEmail("creator1@test.com").orElseThrow();
        User c2 = userRepository.findByEmail("creator2@test.com").orElseThrow();
        String token = jwtService.generateAccessToken(c1);

        UpdateCreatorProfileRequest request = UpdateCreatorProfileRequest.builder()
                .displayName("C1 Update")
                .build();

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify C1 is updated
        CreatorProfile profile1 = creatorProfileRepository.findByUser(c1).orElseThrow();
        assertEquals("C1 Update", profile1.getDisplayName());

        // Verify C2 is NOT updated
        CreatorProfile profile2 = creatorProfileRepository.findByUser(c2).orElseThrow();
        assertEquals("creator2", profile2.getDisplayName()); // Default from initializeCreatorProfile
    }

    @Test
    void updateProfile_AsUser_ShouldReturn403() throws Exception {
        User u = new User();
        u.setEmail("user@test.com");
        u.setUsername("user");
        u.setPassword("password");
        u.setRole(Role.USER);
        userRepository.save(u);
        String token = jwtService.generateAccessToken(u);

        UpdateCreatorProfileRequest request = UpdateCreatorProfileRequest.builder()
                .displayName("User Update")
                .build();

        mockMvc.perform(put("/api/creator/profile")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}








