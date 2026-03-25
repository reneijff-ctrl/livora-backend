package com.joinlivora.backend.service;

import com.joinlivora.backend.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "webp");
    private static final List<String> ALLOWED_VIDEO_EXTENSIONS = Arrays.asList("mp4", "mov", "webm");
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024; // 100MB

    public StoreResult storeCreatorImage(Long creatorId, MultipartFile file, String type) {
        validateContentFile(file); // Reusing validation

        try {
            Path creatorDir = Paths.get(uploadDir, "creators", creatorId.toString());
            if (!Files.exists(creatorDir)) {
                Files.createDirectories(creatorDir);
            }

            String extension = getFileExtension(file.getOriginalFilename());
            String fileName = type.toLowerCase() + "_" + UUID.randomUUID() + "." + extension;
            Path filePath = creatorDir.resolve(fileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("FILE: Stored {} image for creator {}: {}", type, creatorId, fileName);

            return StoreResult.builder()
                    .relativePath("/uploads/creators/" + creatorId + "/" + fileName)
                    .internalFilename(fileName)
                    .absolutePath(filePath.toAbsolutePath().toString())
                    .build();
        } catch (IOException e) {
            log.error("FILE: Failed to store profile file", e);
            throw new BusinessException("Could not store file. Please try again.");
        }
    }

    public StoreResult storeCreatorVerificationFile(Long userId, MultipartFile file, String type) {
        validateContentFile(file);

        try {
            Path verificationDir = Paths.get(uploadDir, "creator-verifications", userId.toString());
            if (!Files.exists(verificationDir)) {
                Files.createDirectories(verificationDir);
            }

            String extension = getFileExtension(file.getOriginalFilename());
            String fileName = type.toLowerCase() + "_" + UUID.randomUUID() + "." + extension;
            Path filePath = verificationDir.resolve(fileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("FILE: Stored verification file for user {}: {}", userId, fileName);

            return StoreResult.builder()
                    .relativePath("/uploads/creator-verifications/" + userId + "/" + fileName)
                    .internalFilename(fileName)
                    .absolutePath(filePath.toAbsolutePath().toString())
                    .build();
        } catch (IOException e) {
            log.error("FILE: Failed to store verification file", e);
            throw new BusinessException("Could not store verification file. Please try again.");
        }
    }

    public StoreResult storeContentFile(Long creatorId, MultipartFile file, String type) {
        validateContentFile(file);

        try {
            Path contentDir = Paths.get(uploadDir, "content", creatorId.toString());
            if (!Files.exists(contentDir)) {
                Files.createDirectories(contentDir);
            }

            String extension = getFileExtension(file.getOriginalFilename());
            String fileName = type.toLowerCase() + "_" + UUID.randomUUID() + "." + extension;
            Path filePath = contentDir.resolve(fileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("FILE: Stored content file for creator {}: {}", creatorId, fileName);

            return StoreResult.builder()
                    .relativePath("/uploads/content/" + creatorId + "/" + fileName)
                    .internalFilename(fileName)
                    .absolutePath(filePath.toAbsolutePath().toString())
                    .build();
        } catch (IOException e) {
            log.error("FILE: Failed to store content file", e);
            throw new BusinessException("Could not store file. Please try again.");
        }
    }

    public StoreResult store(MultipartFile file) {
        validateContentFile(file);

        try {
            Path contentDir = Paths.get(uploadDir, "content");
            if (!Files.exists(contentDir)) {
                Files.createDirectories(contentDir);
            }

            String extension = getFileExtension(file.getOriginalFilename());
            String fileName = UUID.randomUUID() + "." + extension;
            Path filePath = contentDir.resolve(fileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("FILE: Stored general content file: {}", fileName);
            return StoreResult.builder()
                    .relativePath("/uploads/content/" + fileName)
                    .internalFilename(fileName)
                    .absolutePath(filePath.toAbsolutePath().toString())
                    .build();
        } catch (IOException e) {
            log.error("FILE: Failed to store content file", e);
            throw new BusinessException("Could not store file. Please try again.");
        }
    }

    private void validateContentFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("File is empty");
        }

        String contentType = file.getContentType();
        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();

        if (contentType != null && (contentType.startsWith("image/"))) {
            if (file.getSize() > MAX_IMAGE_SIZE) {
                throw new BusinessException("Image size exceeds 5MB limit");
            }
            if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
                throw new BusinessException("Invalid image format. Allowed: " + ALLOWED_IMAGE_EXTENSIONS);
            }
        } else if (contentType != null && (contentType.startsWith("video/"))) {
            if (file.getSize() > MAX_VIDEO_SIZE) {
                throw new BusinessException("Video size exceeds 100MB limit");
            }
            if (!ALLOWED_VIDEO_EXTENSIONS.contains(extension)) {
                throw new BusinessException("Invalid video format. Allowed: " + ALLOWED_VIDEO_EXTENSIONS);
            }
        } else {
            throw new BusinessException("Unsupported file type: " + contentType);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}
