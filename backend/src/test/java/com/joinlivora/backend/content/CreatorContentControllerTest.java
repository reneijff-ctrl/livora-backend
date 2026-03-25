package com.joinlivora.backend.content;

import com.joinlivora.backend.service.FileStorageService;
import com.joinlivora.backend.service.StoreResult;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class CreatorContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContentService contentService;

    @MockBean
    private UserService userService;

    @MockBean
    private FileStorageService fileStorageService;

    private StoreResult createMockStoreResult(String relativePath) {
        return StoreResult.builder()
                .relativePath(relativePath)
                .internalFilename(relativePath.substring(relativePath.lastIndexOf("/") + 1))
                .absolutePath("/absolute/path" + relativePath)
                .build();
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = {"CREATOR"})
    void shouldUploadContent() throws Exception {
        User creator = new User("creator@test.com", "password", com.joinlivora.backend.user.Role.CREATOR);
        creator.setId(1L);

        when(userService.getByEmail(anyString())).thenReturn(creator);
        when(fileStorageService.store(any())).thenReturn(createMockStoreResult("/uploads/content/test.mp4"));

        Content content = Content.builder()
                .id(UUID.randomUUID())
                .title("Test Title")
                .description("Test Description")
                .mediaUrl("/uploads/content/test.mp4")
                .type(ContentType.VIDEO)
                .accessLevel(AccessLevel.PREMIUM)
                .unlockPriceTokens(100)
                .creator(creator)
                .build();

        when(contentService.createContent(any())).thenReturn(content);

        byte[] validVideoHeader = new byte[12];
        validVideoHeader[4] = 'f';
        validVideoHeader[5] = 't';
        validVideoHeader[6] = 'y';
        validVideoHeader[7] = 'p';

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.mp4",
                "video/mp4",
                validVideoHeader
        );

        mockMvc.perform(multipart("/api/creators/content/upload")
                        .file(file)
                        .param("title", "Test Title")
                        .param("description", "Test Description")
                        .param("type", "VIDEO")
                        .param("accessLevel", "PREMIUM")
                        .param("unlockPriceTokens", "100")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Title"))
                .andExpect(jsonPath("$.type").value("VIDEO"))
                .andExpect(jsonPath("$.accessLevel").value("PREMIUM"))
                .andExpect(jsonPath("$.unlockPriceTokens").value(100));

        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
        verify(contentService).createContent(contentCaptor.capture());
        Content capturedContent = contentCaptor.getValue();
        assertThat(capturedContent.getThumbnailUrl()).isEqualTo("/uploads/content/test_thumb.jpg");
        assertThat(capturedContent.getMediaUrl()).isEqualTo("/uploads/content/test.mp4");
        assertThat(capturedContent.getThumbnailUrl()).isNotEqualTo(capturedContent.getMediaUrl());
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = {"CREATOR"})
    void shouldUploadVideoWithNullThumbnailOnFailure() throws Exception {
        User creator = new User("creator@test.com", "password", com.joinlivora.backend.user.Role.CREATOR);
        creator.setId(1L);

        when(userService.getByEmail(anyString())).thenReturn(creator);
        when(fileStorageService.store(any())).thenReturn(createMockStoreResult("/uploads/content/test.mp4"));

        Content content = Content.builder()
                .title("Test Title")
                .mediaUrl("/uploads/content/test.mp4")
                .type(ContentType.VIDEO)
                .accessLevel(AccessLevel.FREE)
                .creator(creator)
                .build();

        when(contentService.createContent(any())).thenReturn(content);

        byte[] validVideoHeader = new byte[12];
        validVideoHeader[4] = 'f';
        validVideoHeader[5] = 't';
        validVideoHeader[6] = 'y';
        validVideoHeader[7] = 'p';

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.mp4",
                "video/mp4",
                validVideoHeader
        );

        mockMvc.perform(multipart("/api/creators/content/upload")
                        .file(file)
                        .param("title", "Test Title")
                        .param("type", "VIDEO")
                        .param("accessLevel", "FREE")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk());

        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
        verify(contentService).createContent(contentCaptor.capture());
        Content capturedContent = contentCaptor.getValue();
        assertThat(capturedContent.getThumbnailUrl()).isNull();
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = {"CREATOR"})
    void shouldFailUploadInvalidFileExtension() throws Exception {
        User creator = new User("creator@test.com", "password", com.joinlivora.backend.user.Role.CREATOR);
        creator.setId(1L);
        when(userService.getByEmail(anyString())).thenReturn(creator);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "video/mp4",
                "test data".getBytes()
        );

        mockMvc.perform(multipart("/api/creators/content/upload")
                        .file(file)
                        .param("title", "Test Title")
                        .param("type", "VIDEO")
                        .param("accessLevel", "FREE")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid file type for selected content type"));
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = {"CREATOR"})
    void shouldFailUploadInvalidMimeType() throws Exception {
        User creator = new User("creator@test.com", "password", com.joinlivora.backend.user.Role.CREATOR);
        creator.setId(1L);
        when(userService.getByEmail(anyString())).thenReturn(creator);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.mp4",
                "image/jpeg",
                "test data".getBytes()
        );

        mockMvc.perform(multipart("/api/creators/content/upload")
                        .file(file)
                        .param("title", "Test Title")
                        .param("type", "VIDEO")
                        .param("accessLevel", "FREE")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid file type for selected content type"));
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = {"CREATOR"})
    void shouldFailUploadInvalidMagicBytes() throws Exception {
        User creator = new User("creator@test.com", "password", com.joinlivora.backend.user.Role.CREATOR);
        creator.setId(1L);
        when(userService.getByEmail(anyString())).thenReturn(creator);

        byte[] invalidHeader = new byte[12]; // No "ftyp"
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.mp4",
                "video/mp4",
                invalidHeader
        );

        mockMvc.perform(multipart("/api/creators/content/upload")
                        .file(file)
                        .param("title", "Test Title")
                        .param("type", "VIDEO")
                        .param("accessLevel", "FREE")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or corrupted video file"));
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = {"CREATOR"})
    void shouldUploadPhoto() throws Exception {
        User creator = new User("creator@test.com", "password", com.joinlivora.backend.user.Role.CREATOR);
        creator.setId(1L);

        when(userService.getByEmail(anyString())).thenReturn(creator);
        when(fileStorageService.store(any())).thenReturn(createMockStoreResult("/uploads/content/test.jpg"));

        Content content = Content.builder()
                .id(UUID.randomUUID())
                .title("Photo Title")
                .mediaUrl("/uploads/content/test.jpg")
                .thumbnailUrl("/uploads/content/test.jpg")
                .type(ContentType.PHOTO)
                .accessLevel(AccessLevel.FREE)
                .creator(creator)
                .build();

        when(contentService.createContent(any())).thenReturn(content);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "test data".getBytes()
        );

        mockMvc.perform(multipart("/api/creators/content/upload")
                        .file(file)
                        .param("title", "Photo Title")
                        .param("type", "PHOTO")
                        .param("accessLevel", "FREE")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Photo Title"))
                .andExpect(jsonPath("$.type").value("PHOTO"));

        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
        verify(contentService).createContent(contentCaptor.capture());
        Content capturedContent = contentCaptor.getValue();
        assertThat(capturedContent.getThumbnailUrl()).isEqualTo("/uploads/content/test.jpg");
        assertThat(capturedContent.getMediaUrl()).isEqualTo("/uploads/content/test.jpg");
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = {"CREATOR"})
    void shouldFailPhotoUploadWithVideoFile() throws Exception {
        User creator = new User("creator@test.com", "password", com.joinlivora.backend.user.Role.CREATOR);
        creator.setId(1L);
        when(userService.getByEmail(anyString())).thenReturn(creator);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.mp4",
                "video/mp4",
                "test data".getBytes()
        );

        mockMvc.perform(multipart("/api/creators/content/upload")
                        .file(file)
                        .param("title", "Test Title")
                        .param("type", "PHOTO")
                        .param("accessLevel", "FREE")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid file type for selected content type"));
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = {"CREATOR"})
    void shouldUploadClip() throws Exception {
        User creator = new User("creator@test.com", "password", com.joinlivora.backend.user.Role.CREATOR);
        creator.setId(1L);

        when(userService.getByEmail(anyString())).thenReturn(creator);
        when(fileStorageService.store(any())).thenReturn(createMockStoreResult("/uploads/content/test.mp4"));

        Content content = Content.builder()
                .id(UUID.randomUUID())
                .title("Clip Title")
                .mediaUrl("/uploads/content/test.mp4")
                .type(ContentType.CLIP)
                .accessLevel(AccessLevel.FREE)
                .creator(creator)
                .build();

        when(contentService.createContent(any())).thenReturn(content);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.mp4",
                "video/mp4",
                "test data".getBytes()
        );

        mockMvc.perform(multipart("/api/creators/content/upload")
                        .file(file)
                        .param("title", "Clip Title")
                        .param("type", "CLIP")
                        .param("accessLevel", "FREE")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Clip Title"))
                .andExpect(jsonPath("$.type").value("CLIP"));
    }
}








