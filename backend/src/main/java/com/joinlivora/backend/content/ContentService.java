package com.joinlivora.backend.content;

import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentService {
    private final ContentRepository contentRepository;

    public List<Content> getPublicContent() {
        return contentRepository.findByAccessLevelAndDisabledFalse(ContentAccessLevel.FREE);
    }

    public List<Content> getFeedContent(User user, boolean hasPremium) {
        List<ContentAccessLevel> levels = new ArrayList<>();
        levels.add(ContentAccessLevel.FREE);
        
        if (hasPremium || user.getRole().name().equals("ADMIN") || user.getRole().name().equals("CREATOR")) {
            levels.add(ContentAccessLevel.PREMIUM);
        }
        
        if (user.getRole().name().equals("ADMIN") || user.getRole().name().equals("CREATOR")) {
            levels.add(ContentAccessLevel.CREATOR);
        }
        
        return contentRepository.findByAccessLevelInAndDisabledFalse(levels);
    }

    public List<Content> getPremiumContent() {
        return contentRepository.findByAccessLevelAndDisabledFalse(ContentAccessLevel.PREMIUM);
    }

    public List<Content> getCreatorContent(User creator) {
        return contentRepository.findByCreatorId(creator.getId());
    }

    public Content getContentById(UUID id) {
        return contentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Content not found"));
    }

    @Transactional
    public Content createContent(Content content) {
        return contentRepository.save(content);
    }

    @Transactional
    public Content updateContent(UUID id, Content contentDetails, User creator) {
        Content content = getContentById(id);
        if (!content.getCreator().getId().equals(creator.getId()) && !creator.getRole().name().equals("ADMIN")) {
            throw new RuntimeException("Not authorized to update this content");
        }
        
        content.setTitle(contentDetails.getTitle());
        content.setDescription(contentDetails.getDescription());
        content.setThumbnailUrl(contentDetails.getThumbnailUrl());
        content.setAccessLevel(contentDetails.getAccessLevel());
        
        return contentRepository.save(content);
    }

    @Transactional
    public void deleteContent(UUID id, User user) {
        Content content = getContentById(id);
        if (user != null) {
            if (!content.getCreator().getId().equals(user.getId()) && !user.getRole().name().equals("ADMIN")) {
                throw new RuntimeException("Not authorized to delete this content");
            }
        }
        // If user is null, we assume it's an internal call or already authorized (like from AdminContentController)
        contentRepository.delete(content);
    }

    @Transactional
    public void disableContent(UUID id) {
        Content content = getContentById(id);
        content.setDisabled(true);
        contentRepository.save(content);
    }

    public List<Content> getAllContentForAdmin() {
        return contentRepository.findAll();
    }
}
