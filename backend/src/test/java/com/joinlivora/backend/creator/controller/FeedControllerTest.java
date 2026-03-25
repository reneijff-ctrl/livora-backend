package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.follow.entity.CreatorFollow;
import com.joinlivora.backend.creator.follow.repository.CreatorFollowRepository;
import com.joinlivora.backend.creator.model.CreatorPost;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.repository.CreatorPostRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private CreatorPostRepository creatorPostRepository;

    @Autowired
    private CreatorFollowRepository followRepository;

    @Autowired
    private JwtService jwtService;

    private User follower;
    private User creator1;
    private User creator2;
    private User notFollowedCreator;

    @BeforeEach
    void setUp() {
        follower = createUser("follower@test.com", Role.USER);
        creator1 = createUser("creator1@test.com", Role.CREATOR);
        creator2 = createUser("creator2@test.com", Role.CREATOR);
        notFollowedCreator = createUser("notfollowed@test.com", Role.CREATOR);

        createProfile(creator1, "Creator One", "creator1");
        createProfile(creator2, "Creator Two", "creator2");
        createProfile(notFollowedCreator, "Not Followed", "notfollowed");

        followRepository.save(CreatorFollow.builder().follower(follower).creator(creator1).build());
        followRepository.save(CreatorFollow.builder().follower(follower).creator(creator2).build());

        createPost(creator1, "C1 Post", "Content 1");
        createPost(creator2, "C2 Post", "Content 2");
        createPost(notFollowedCreator, "NF Post", "NF Content");
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("password");
        user.setRole(role);
        user.setUsername(email.split("@")[0]);
        return userRepository.save(user);
    }

    private void createProfile(User user, String displayName, String username) {
        CreatorProfile profile = new CreatorProfile();
        profile.setUser(user);
        profile.setDisplayName(displayName);
        profile.setUsername(username);
        profile.setStatus(com.joinlivora.backend.creator.model.ProfileStatus.ACTIVE);
        creatorProfileRepository.save(profile);
    }

    private void createPost(User creator, String title, String content) {
        CreatorPost post = CreatorPost.builder()
                .creator(creator)
                .title(title)
                .content(content)
                .createdAt(Instant.now())
                .build();
        creatorPostRepository.save(post);
    }

    @Test
    void getFeed_ShouldReturnPostsFromFollowedCreators() throws Exception {
        String token = jwtService.generateAccessToken(follower);

        mockMvc.perform(get("/api/feed")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[?(@.title == 'C1 Post')]").exists())
                .andExpect(jsonPath("$.content[?(@.title == 'C2 Post')]").exists())
                .andExpect(jsonPath("$.content[?(@.title == 'NF Post')]").doesNotExist())
                .andExpect(jsonPath("$.content[0].displayName").exists())
                .andExpect(jsonPath("$.content[0].creatorId").exists());
    }

    @Test
    void getFeed_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/feed")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}








