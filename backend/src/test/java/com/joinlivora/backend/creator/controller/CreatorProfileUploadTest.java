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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreatorProfileUploadTest {

    @org.junit.jupiter.api.AfterAll
    static void cleanup() throws IOException {
        Path testUploads = Paths.get("uploads");
        if (Files.exists(testUploads)) {
            Files.walk(testUploads)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private JwtService jwtService;

    private User creatorUser;
    private User regularUser;
    private User otherCreator;

    @BeforeEach
    void setUp() {
        creatorUser = new User();
        creatorUser.setEmail("creator-upload@test.com");
        creatorUser.setUsername("creator-upload");
        creatorUser.setPassword("password");
        creatorUser.setRole(Role.CREATOR);
        creatorUser = userRepository.save(creatorUser);

        CreatorProfile profile = CreatorProfile.builder()
                .user(creatorUser)
                .username("creator_upload")
                .displayName("Upload Creator")
                .build();
        creatorProfileRepository.save(profile);

        regularUser = new User();
        regularUser.setEmail("user-upload@test.com");
        regularUser.setUsername("user-upload");
        regularUser.setPassword("password");
        regularUser.setRole(Role.USER);
        regularUser = userRepository.save(regularUser);

        otherCreator = new User();
        otherCreator.setEmail("other-creator@test.com");
        otherCreator.setUsername("other-creator");
        otherCreator.setPassword("password");
        otherCreator.setRole(Role.CREATOR);
        otherCreator = userRepository.save(otherCreator);

        CreatorProfile otherProfile = CreatorProfile.builder()
                .user(otherCreator)
                .username("other_creator")
                .displayName("Other Creator")
                .build();
        creatorProfileRepository.save(otherProfile);
    }

    @Test
    void uploadProfileImage_AsCreator_ShouldSucceed() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-image-content".getBytes()
        );

        mockMvc.perform(multipart("/api/creator/profile/upload")
                        .file(file)
                        .param("type", "PROFILE")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(containsString("/uploads/creators/" + creatorUser.getId() + "/profile_")))
                .andExpect(jsonPath("$.avatarUrl").value(containsString(".jpg")));

        CreatorProfile profile = creatorProfileRepository.findByUser(creatorUser).orElseThrow();
        assertTrue(profile.getAvatarUrl().contains("profile_"));
        
        // Cleanup: remove created directory in uploads if it was created
        // Note: In a real CI environment we might want to mock the storage or use a temporary folder
    }

    @Test
    void uploadBannerImage_AsCreator_ShouldSucceed() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "banner.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-banner-content".getBytes()
        );

        mockMvc.perform(multipart("/api/creator/profile/upload")
                        .file(file)
                        .param("type", "BANNER")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannerUrl").value(containsString("/uploads/creators/" + creatorUser.getId() + "/banner_")))
                .andExpect(jsonPath("$.bannerUrl").value(containsString(".png")));

        CreatorProfile profile = creatorProfileRepository.findByUser(creatorUser).orElseThrow();
        assertTrue(profile.getBannerUrl().contains("banner_"));
    }

    @Test
    void uploadImage_AsRegularUser_ShouldReturnForbidden() throws Exception {
        String token = jwtService.generateAccessToken(regularUser);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/creator/profile/upload")
                        .file(file)
                        .param("type", "PROFILE")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadImage_InvalidType_ShouldReturnBadRequest() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/creator/profile/upload")
                        .file(file)
                        .param("type", "INVALID")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadImage_FileTooLarge_ShouldReturnBadRequest() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                largeContent
        );

        mockMvc.perform(multipart("/api/creator/profile/upload")
                        .file(file)
                        .param("type", "PROFILE")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadImage_NonImageFile_ShouldReturnBadRequest() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not an image".getBytes()
        );

        mockMvc.perform(multipart("/api/creator/profile/upload")
                        .file(file)
                        .param("type", "PROFILE")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadImage_OtherCreator_ShouldReturnForbidden() throws Exception {
        String token = jwtService.generateAccessToken(creatorUser);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/creator/profile/upload")
                        .file(file)
                        .param("type", "PROFILE")
                        .param("creator", otherCreator.getId().toString())
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadImage_ProfileNotFound_ShouldReturnNotFound() throws Exception {
        User creatorWithoutProfile = new User();
        creatorWithoutProfile.setEmail("no-profile@test.com");
        creatorWithoutProfile.setUsername("no-profile");
        creatorWithoutProfile.setPassword("password");
        creatorWithoutProfile.setRole(Role.CREATOR);
        creatorWithoutProfile = userRepository.save(creatorWithoutProfile);

        String token = jwtService.generateAccessToken(creatorWithoutProfile);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/creator/profile/upload")
                        .file(file)
                        .param("type", "PROFILE")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}








