package com.joinlivora.backend.content.migration;

import com.joinlivora.backend.content.Content;
import com.joinlivora.backend.content.ContentRepository;
import com.joinlivora.backend.content.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ContentThumbnailMigrationTest {

    private ContentRepository contentRepository;
    private ContentThumbnailMigration migration;
    private Path tempUploadDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        contentRepository = mock(ContentRepository.class);
        this.tempUploadDir = tempDir;
        migration = new ContentThumbnailMigration(contentRepository, tempUploadDir.toString());
    }

    @Test
    void migrateThumbnails_ShouldUpdateWhenFileExists() throws IOException {
        // Given
        UUID contentId = UUID.randomUUID();
        Content content = Content.builder()
                .id(contentId)
                .type(ContentType.VIDEO)
                .thumbnailUrl("/uploads/content/video.mp4")
                .build();

        // Create the actual thumbnail file in the temp upload dir
        Path contentDir = tempUploadDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.createFile(contentDir.resolve("video_thumb.jpg"));

        when(contentRepository.findAll()).thenReturn(List.of(content));

        // When
        migration.migrateThumbnails();

        // Then
        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
        verify(contentRepository, times(1)).save(contentCaptor.capture());
        assertEquals("/uploads/content/video_thumb.jpg", contentCaptor.getValue().getThumbnailUrl());
    }

    @Test
    void migrateThumbnails_ShouldNotUpdateWhenFileDoesNotExist() throws IOException {
        // Given
        UUID contentId = UUID.randomUUID();
        Content content = Content.builder()
                .id(contentId)
                .type(ContentType.VIDEO)
                .thumbnailUrl("/uploads/content/video.mp4")
                .build();

        // Ensure thumbnail directory exists but file doesn't
        Files.createDirectories(tempUploadDir.resolve("content"));

        when(contentRepository.findAll()).thenReturn(List.of(content));

        // When
        migration.migrateThumbnails();

        // Then
        verify(contentRepository, never()).save(any(Content.class));
    }

    @Test
    void migrateThumbnails_ShouldNotAffectNonVideoContent() {
        // Given
        Content content = Content.builder()
                .type(ContentType.PHOTO)
                .thumbnailUrl("/uploads/content/image.mp4") // Unlikely but good for test
                .build();

        when(contentRepository.findAll()).thenReturn(List.of(content));

        // When
        migration.migrateThumbnails();

        // Then
        verify(contentRepository, never()).save(any(Content.class));
    }

    @Test
    void migrateThumbnails_ShouldNotAffectVideoWithCorrectThumbnail() {
        // Given
        Content content = Content.builder()
                .type(ContentType.VIDEO)
                .thumbnailUrl("/uploads/content/video_thumb.jpg")
                .build();

        when(contentRepository.findAll()).thenReturn(List.of(content));

        // When
        migration.migrateThumbnails();

        // Then
        verify(contentRepository, never()).save(any(Content.class));
    }
}








