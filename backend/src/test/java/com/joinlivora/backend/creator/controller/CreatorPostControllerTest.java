package com.joinlivora.backend.creator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.creator.dto.CreateCreatorPostRequest;
import com.joinlivora.backend.creator.model.CreatorPost;
import com.joinlivora.backend.creator.service.CreatorPostService;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreatorPostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.joinlivora.backend.creator.repository.CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CreatorPostService creatorPostService;

    private User creatorUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        creatorUser = new User();
        creatorUser.setEmail("creator-post@test.com");
        creatorUser.setPassword("password");
        creatorUser.setRole(com.joinlivora.backend.user.Role.CREATOR);
        creatorUser.setUsername("creator-post");
        creatorUser = userRepository.save(creatorUser);

        com.joinlivora.backend.creator.model.CreatorProfile profile = new com.joinlivora.backend.creator.model.CreatorProfile();
        profile.setUser(creatorUser);
        profile.setUsername("creator_post_user");
        profile.setDisplayName("Creator Post User");
        profile.setStatus(com.joinlivora.backend.creator.model.ProfileStatus.ACTIVE);
        creatorProfileRepository.save(profile);

        regularUser = new User();
        regularUser.setEmail("user-post@test.com");
        regularUser.setPassword("password");
        regularUser.setRole(com.joinlivora.backend.user.Role.USER);
        regularUser.setUsername("user-post");
        regularUser = userRepository.save(regularUser);
    }

    @Test
    void createPost_AsCreator_ShouldSucceed() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        Long profileId = creatorProfileRepository.findByUser(creatorUser).get().getId();
        CreateCreatorPostRequest request = CreateCreatorPostRequest.builder()
                .title("My First Post")
                .content("This is the content of my post")
                .build();

        mockMvc.perform(post("/api/creator/posts")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("My First Post"))
                .andExpect(jsonPath("$.content").value("This is the content of my post"))
                .andExpect(jsonPath("$.creatorId").value(profileId))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createPost_AsRegularUser_ShouldReturnForbidden() throws Exception {
        String token = jwtService.generateAccessToken(regularUser);
        CreateCreatorPostRequest request = CreateCreatorPostRequest.builder()
                .title("Unauthorized Post")
                .content("Content")
                .build();

        mockMvc.perform(post("/api/creator/posts")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPosts_PublicAccess_ShouldSucceed() throws Exception {
        creatorPostService.createPost(creatorUser, "Post 1", "Content 1");
        Thread.sleep(10); // Ensure different timestamps
        creatorPostService.createPost(creatorUser, "Post 2", "Content 2");

        // By username
        mockMvc.perform(get("/api/creators/creator_post_user/posts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Post 2")) // Newest first
                .andExpect(jsonPath("$[1].title").value("Post 1"));

        // By ID
        Long profileId = creatorProfileRepository.findByUser(creatorUser).get().getId();
        mockMvc.perform(get("/api/creators/" + profileId + "/posts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Post 2"));
    }

    @Test
    void getPosts_Empty_ShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/creators/nonexistent/posts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getPublicPosts_ShouldSucceed() throws Exception {
        creatorPostService.createPost(creatorUser, "Public Post", "Public Content");

        mockMvc.perform(get("/api/posts/public")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].title").value("Public Post"))
                .andExpect(jsonPath("$.content[0].username").value("creator_post_user"))
                .andExpect(jsonPath("$.content[0].displayName").value("Creator Post User"))
                .andExpect(jsonPath("$.content[0].likeCount").exists());
    }

    @Test
    void getPost_ShouldSucceed() throws Exception {
        com.joinlivora.backend.creator.model.CreatorPost post = creatorPostService.createPost(creatorUser, "Single Post", "Single Content");
        String token = jwtService.generateAccessToken(regularUser);

        // Authenticated
        mockMvc.perform(get("/api/posts/" + post.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Single Post"))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByMe").value(false));

        // Like it
        creatorPostService.likePost(regularUser, post.getId());

        mockMvc.perform(get("/api/posts/" + post.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(true));

        // Public access
        mockMvc.perform(get("/api/posts/" + post.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(false));
    }
}








