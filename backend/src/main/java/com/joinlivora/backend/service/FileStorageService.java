package com.joinlivora.backend.service;

import com.joinlivora.backend.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "webp");
    private static final List<String> ALLOWED_VIDEO_EXTENSIONS = Arrays.asList("mp4", "mov", "webm");
    private static final Set<String> ALLOWED_IMAGE_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_VIDEO_MIME_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/webm");
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024; // 100MB

    // Magic byte signatures for image validation
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] WEBP_RIFF = new byte[]{0x52, 0x49, 0x46, 0x46}; // "RIFF"
    private static final byte[] WEBP_WEBP = new byte[]{0x57, 0x45, 0x42, 0x50}; // "WEBP" at offset 8

    private static final Map<String, byte[]> IMAGE_MAGIC_BYTES = Map.of(
            "image/jpeg", JPEG_MAGIC,
            "image/png", PNG_MAGIC
    );

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

        if (contentType != null && contentType.startsWith("image/")) {
            if (!ALLOWED_IMAGE_MIME_TYPES.contains(contentType)) {
                throw new BusinessException("Invalid image MIME type: " + contentType);
            }
            if (file.getSize() > MAX_IMAGE_SIZE) {
                throw new BusinessException("Image size exceeds 5MB limit");
            }
            if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
                throw new BusinessException("Invalid image format. Allowed: " + ALLOWED_IMAGE_EXTENSIONS);
            }
            validateMagicBytes(file, contentType);
        } else if (contentType != null && contentType.startsWith("video/")) {
            if (!ALLOWED_VIDEO_MIME_TYPES.contains(contentType)) {
                throw new BusinessException("Invalid video MIME type: " + contentType);
            }
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

    private void validateMagicBytes(MultipartFile file, String contentType) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int bytesRead = is.read(header);
            if (bytesRead < 4) {
                throw new BusinessException("File too small to validate");
            }

            // Check WebP separately (RIFF....WEBP)
            if ("image/webp".equals(contentType)) {
                if (!startsWith(header, WEBP_RIFF) || bytesRead < 12
                        || header[8] != WEBP_WEBP[0] || header[9] != WEBP_WEBP[1]
                        || header[10] != WEBP_WEBP[2] || header[11] != WEBP_WEBP[3]) {
                    throw new BusinessException("File content does not match declared WebP type");
                }
                return;
            }

            // Check JPEG / PNG magic bytes
            byte[] expectedMagic = IMAGE_MAGIC_BYTES.get(contentType);
            if (expectedMagic != null && !startsWith(header, expectedMagic)) {
                throw new BusinessException("File content does not match declared type: " + contentType);
            }
        } catch (IOException e) {
            throw new BusinessException("Could not validate file content");
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}
