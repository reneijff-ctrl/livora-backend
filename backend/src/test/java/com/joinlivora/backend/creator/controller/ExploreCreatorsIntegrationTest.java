package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
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

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExploreCreatorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @MockBean
    private com.joinlivora.backend.creator.service.OnlineStatusService onlineStatusService;

    @BeforeEach
    void setUp() {
        creatorProfileRepository.deleteAll();
        userRepository.deleteAll();

        User user1 = new User();
        user1.setEmail("creator1@test.com");
        user1.setUsername("creator1");
        user1.setPassword("password");
        user1.setRole(Role.CREATOR);
        user1 = userRepository.save(user1);

        CreatorProfile profile1 = CreatorProfile.builder()
                .user(user1)
                .username("creator1")
                .displayName("Creator One")
                .avatarUrl("http://example.com/avatar1.jpg")
                .bannerUrl("http://example.com/banner1.jpg")
                .bio("Bio one")
                .status(ProfileStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
        creatorProfileRepository.save(profile1);

        User user2 = new User();
        user2.setEmail("creator2@test.com");
        user2.setUsername("creator2");
        user2.setPassword("password");
        user2.setRole(Role.CREATOR);
        user2 = userRepository.save(user2);

        CreatorProfile profile2 = CreatorProfile.builder()
                .user(user2)
                .username("creator2")
                .displayName("Creator Two")
                .avatarUrl("http://example.com/avatar2.jpg")
                .bannerUrl("http://example.com/banner2.jpg")
                .bio("Bio two")
                .status(ProfileStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
        creatorProfileRepository.save(profile2);
    }

    @Test
    void getExploreCreators_ShouldReturnPublicFields() throws Exception {
        mockMvc.perform(get("/api/public/creators")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].creatorId").exists())
                .andExpect(jsonPath("$.content[0].displayName").exists())
                .andExpect(jsonPath("$.content[0].profileImageUrl").exists())
                .andExpect(jsonPath("$.content[0].isOnline").exists())
                .andExpect(jsonPath("$.content[0].shortBio").exists())
                .andExpect(jsonPath("$.content[0].bannerImageUrl").exists())
                .andExpect(jsonPath("$.content[0].username").exists());
    }

    @Test
    void getExploreCreators_WhenEmpty_ShouldReturnEmptyPage() throws Exception {
        creatorProfileRepository.deleteAll();
        userRepository.deleteAll();

        mockMvc.perform(get("/api/public/creators")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }
}








