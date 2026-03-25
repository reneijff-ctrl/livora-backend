package com.joinlivora.backend.content.migration;

import com.joinlivora.backend.content.Content;
import com.joinlivora.backend.content.ContentRepository;
import com.joinlivora.backend.content.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ContentThumbnailMigration {

    private final ContentRepository contentRepository;
    private final String uploadDir;

    public ContentThumbnailMigration(
            ContentRepository contentRepository,
            @Value("${file.upload-dir:uploads}") String uploadDir) {
        this.contentRepository = contentRepository;
        this.uploadDir = uploadDir;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateThumbnails() {
        log.info("MIGRATION: Starting content thumbnail migration check...");

        List<Content> videoContents = contentRepository.findAll().stream()
                .filter(c -> c.getType() == ContentType.VIDEO)
                .filter(c -> c.getThumbnailUrl() != null && c.getThumbnailUrl().endsWith(".mp4"))
                .collect(Collectors.toList());

        if (videoContents.isEmpty()) {
            log.info("MIGRATION: No video content found requiring thumbnail update.");
            return;
        }

        log.info("MIGRATION: Found {} video content(s) with potential .mp4 thumbnail URLs.", videoContents.size());

        for (Content content : videoContents) {
            String originalThumbnailUrl = content.getThumbnailUrl();
            String newThumbnailUrl = originalThumbnailUrl.replace(".mp4", "_thumb.jpg");

            // The stored path starts with /uploads/ which maps to uploadDir
            if (!originalThumbnailUrl.startsWith("/uploads/")) {
                log.warn("MIGRATION: Skipping content {}: thumbnailUrl '{}' does not start with /uploads/",
                        content.getId(), originalThumbnailUrl);
                continue;
            }

            // Extract the path after /uploads/ to build the filesystem path
            String thumbRelativeToUploads = originalThumbnailUrl.substring("/uploads".length()).replace(".mp4", "_thumb.jpg");
            
            // Remove leading slash if present to safely resolve with uploadDir
            String pathForResolve = thumbRelativeToUploads.startsWith("/") ? thumbRelativeToUploads.substring(1) : thumbRelativeToUploads;
            Path thumbPhysicalPath = Paths.get(uploadDir).resolve(pathForResolve).toAbsolutePath().normalize();

            if (Files.exists(thumbPhysicalPath)) {
                content.setThumbnailUrl(newThumbnailUrl);
                contentRepository.save(content);
                log.info("MIGRATION: Updated content {} thumbnail: {} -> {}", 
                        content.getId(), originalThumbnailUrl, newThumbnailUrl);
            } else {
                log.warn("MIGRATION: Thumbnail file not found for content {}: {}", 
                        content.getId(), thumbPhysicalPath);
            }
        }
        log.info("MIGRATION: Finished content thumbnail migration.");
    }
}
