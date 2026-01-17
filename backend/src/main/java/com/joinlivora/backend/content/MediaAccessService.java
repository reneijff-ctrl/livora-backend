package com.joinlivora.backend.content;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class MediaAccessService {

    public String generateSignedUrl(Content content) {
        // In a real implementation, this would interact with S3 or a CDN to generate a signed URL
        // with a limited expiration time (e.g., 1 hour).
        // For now, we simulate this by appending a dummy signature and expiration.
        
        long expiration = Instant.now().getEpochSecond() + 3600;
        String dummySignature = UUID.randomUUID().toString().substring(0, 8);
        
        String signedUrl = content.getMediaUrl() + "?expires=" + expiration + "&signature=" + dummySignature;
        
        log.info("SECURITY: Generated signed URL for content {}. Expiration: {}", content.getId(), expiration);
        
        return signedUrl;
    }
}
