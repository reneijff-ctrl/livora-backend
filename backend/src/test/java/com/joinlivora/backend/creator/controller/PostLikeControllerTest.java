package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.model.CreatorPost;
import com.joinlivora.backend.creator.repository.CreatorPostRepository;
import com.joinlivora.backend.creator.repository.PostLikeRepository;
import com.joinlivora.backend.security.JwtService;
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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PostLikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorPostRepository creatorPostRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private JwtService jwtService;

    private User user;
    private CreatorPost post;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("liker@test.com");
        user.setUsername("liker");
        user.setPassword("password");
        user.setRole(com.joinlivora.backend.user.Role.USER);
        user = userRepository.save(user);

        User creator = new User();
        creator.setEmail("creator-like@test.com");
        creator.setUsername("creator-like");
        creator.setPassword("password");
        creator.setRole(com.joinlivora.backend.user.Role.CREATOR);
        creator = userRepository.save(creator);

        post = CreatorPost.builder()
                .creator(creator)
                .title("Test Post")
                .content("Test Content")
                .build();
        post = creatorPostRepository.save(post);
    }

    @Test
    void likePost_ShouldSucceed() throws Exception {
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(post("/api/posts/" + post.getId() + "/like")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assert postLikeRepository.existsByUserAndPost(user, post);
    }

    @Test
    void likePost_Idempotent_ShouldSucceed() throws Exception {
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(post("/api/posts/" + post.getId() + "/like")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/posts/" + post.getId() + "/like")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assert postLikeRepository.countByPostId(post.getId()) == 1;
    }

    @Test
    void unlikePost_ShouldSucceed() throws Exception {
        String token = jwtService.generateAccessToken(user);

        // Like first
        mockMvc.perform(post("/api/posts/" + post.getId() + "/like")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Then unlike
        mockMvc.perform(delete("/api/posts/" + post.getId() + "/like")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assert !postLikeRepository.existsByUserAndPost(user, post);
    }

    @Test
    void getLikeCount_ShouldReturnCorrectCount() throws Exception {
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/posts/" + post.getId() + "/likes/count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        mockMvc.perform(post("/api/posts/" + post.getId() + "/like")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/posts/" + post.getId() + "/likes/count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    void getLikeCount_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/posts/" + post.getId() + "/likes/count"))
                .andExpect(status().isUnauthorized());
    }
}








