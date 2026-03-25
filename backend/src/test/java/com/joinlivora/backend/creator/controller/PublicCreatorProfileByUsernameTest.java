package com.joinlivora.backend.creator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.repository.CreatorPostRepository;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.creator.service.CreatorPostService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicCreatorProfileByUsernameTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private CreatorPostService creatorPostService;

    private User creatorUser;
    private CreatorProfile creatorProfile;
    private User shadowbannedCreator;
    private User regularUser;

    @BeforeEach
    void setUp() {
        creatorUser = new User();
        creatorUser.setEmail("creator-profile@test.com");
        creatorUser.setUsername("creator-profile");
        creatorUser.setPassword("password");
        creatorUser.setRole(Role.CREATOR);
        creatorUser = userRepository.save(creatorUser);

        creatorProfile = CreatorProfile.builder()
                .user(creatorUser)
                .username("creator_profile_user")
                .displayName("Creator Profile User")
                .bio("This is a bio")
                .avatarUrl("http://avatar.url")
                .bannerUrl("http://banner.url")
                .status(ProfileStatus.ACTIVE)
                .build();
        creatorProfile = creatorProfileRepository.save(creatorProfile);

        shadowbannedCreator = new User();
        shadowbannedCreator.setEmail("shadow-profile@test.com");
        shadowbannedCreator.setUsername("shadow-profile");
        shadowbannedCreator.setPassword("password");
        shadowbannedCreator.setRole(Role.CREATOR);
        shadowbannedCreator.setShadowbanned(true);
        shadowbannedCreator = userRepository.save(shadowbannedCreator);

        CreatorProfile shadowProfile = CreatorProfile.builder()
                .user(shadowbannedCreator)
                .username("shadow_user")
                .displayName("Shadow User")
                .status(ProfileStatus.ACTIVE)
                .build();
        creatorProfileRepository.save(shadowProfile);

        regularUser = new User();
        regularUser.setEmail("regular-user@test.com");
        regularUser.setUsername("regular-user");
        regularUser.setPassword("password");
        regularUser.setRole(Role.USER);
        regularUser = userRepository.save(regularUser);

        CreatorProfile userProfile = CreatorProfile.builder()
                .user(regularUser)
                .username("regular_user")
                .displayName("Regular User")
                .status(ProfileStatus.ACTIVE)
                .build();
        creatorProfileRepository.save(userProfile);
    }

    @Test
    void getProfileByUsername_ShouldSucceedForValidCreator() throws Exception {
        creatorPostService.createPost(creatorUser, "Post 1", "Content 1");
        creatorPostService.createPost(creatorUser, "Post 2", "Content 2");

        mockMvc.perform(get("/api/creators/username/creator_profile_user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorId").value(creatorProfile.getId()))
                .andExpect(jsonPath("$.username").value("creator_profile_user"))
                .andExpect(jsonPath("$.displayName").value("Creator Profile User"))
                .andExpect(jsonPath("$.bio").value("This is a bio"))
                .andExpect(jsonPath("$.profileImageUrl").value("http://avatar.url"))
                .andExpect(jsonPath("$.bannerImageUrl").value("http://banner.url"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.totalPosts").value(2));
    }

    @Test
    void getProfileByUsername_ShouldReturnNotFoundForShadowbannedCreator() throws Exception {
        mockMvc.perform(get("/api/creators/username/shadow_user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProfileByUsername_ShouldReturnNotFoundForNonCreator() throws Exception {
        mockMvc.perform(get("/api/creators/username/regular_user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProfileByUsername_ShouldReturnNotFoundForNonExistentUser() throws Exception {
        mockMvc.perform(get("/api/creators/username/nonexistent")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}








