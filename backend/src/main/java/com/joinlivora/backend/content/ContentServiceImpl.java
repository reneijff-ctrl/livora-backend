package com.joinlivora.backend.content;

import com.joinlivora.backend.exception.PermissionDeniedException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.content.dto.UpdateContentRequest;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentServiceImpl implements ContentService {
    private final ContentRepository contentRepository;
    private final ContentUnlockRepository contentUnlockRepository;

    public List<Content> getPublicContent() {
        return contentRepository.findByAccessLevelAndDisabledFalseAndCreatorShadowbannedFalse(AccessLevel.FREE);
    }

    public List<Content> getFeedContent(User user, boolean hasPremium) {
        List<AccessLevel> levels = new ArrayList<>();
        levels.add(AccessLevel.FREE);
        
        if (hasPremium || user.getRole().name().equals("ADMIN") || user.getRole().name().equals("CREATOR")) {
            levels.add(AccessLevel.PREMIUM);
        }
        
        if (user.getRole().name().equals("ADMIN") || user.getRole().name().equals("CREATOR")) {
            levels.add(AccessLevel.CREATOR);
        }
        
        return contentRepository.findByAccessLevelInAndDisabledFalseAndCreatorShadowbannedFalse(levels);
    }

    public List<Content> getPremiumContent() {
        return contentRepository.findByAccessLevelAndDisabledFalseAndCreatorShadowbannedFalse(AccessLevel.PREMIUM);
    }

    public List<Content> getCreatorContent(User creator) {
        return contentRepository.findByCreatorIdOrderByCreatedAtDesc(creator.getId());
    }

    public List<Content> getContentByCreatorId(Long creatorId) {
        return contentRepository.findByCreatorId(creatorId);
    }

    public Content getContentById(UUID id) {
        return contentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Content not found with id: " + id));
    }

    @Transactional
    @CacheEvict(value = "publicContent", allEntries = true)
    public Content createContent(Content content) {
        return contentRepository.save(content);
    }

    @Transactional
    @CacheEvict(value = "publicContent", allEntries = true)
    public Content updateContent(UUID id, Content contentDetails, User creator) {
        Content content = getContentById(id);
        if (!content.getCreator().getId().equals(creator.getId()) && !creator.getRole().name().equals("ADMIN")) {
            throw new PermissionDeniedException("You are not authorized to update this content");
        }
        
        content.setTitle(contentDetails.getTitle());
        content.setDescription(contentDetails.getDescription());
        content.setThumbnailUrl(contentDetails.getThumbnailUrl());
        content.setAccessLevel(contentDetails.getAccessLevel());
        content.setType(contentDetails.getType());
        
        Content updatedContent = contentRepository.save(content);
        Hibernate.initialize(updatedContent.getCreator());
        return updatedContent;
    }

    @Override
    @Transactional
    @CacheEvict(value = "publicContent", allEntries = true)
    public Content updateContent(UUID contentId, Long creatorId, UpdateContentRequest request) {
        Content content = getContentById(contentId);
        
        if (!content.getCreator().getId().equals(creatorId)) {
            throw new AccessDeniedException("You are not authorized to update this content");
        }
        
        if (request.getTitle() != null) {
            content.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            content.setDescription(request.getDescription());
        }
        if (request.getAccessLevel() != null) {
            content.setAccessLevel(AccessLevel.valueOf(request.getAccessLevel().toUpperCase()));
        }
        if (request.getUnlockPriceTokens() != null) {
            content.setUnlockPriceTokens(request.getUnlockPriceTokens());
        }
        
        Content updatedContent = contentRepository.save(content);
        Hibernate.initialize(updatedContent.getCreator());
        return updatedContent;
    }

    @Transactional
    @CacheEvict(value = "publicContent", allEntries = true)
    public void deleteContent(UUID id, User user) {
        Content content = getContentById(id);
        if (user != null) {
            if (!content.getCreator().getId().equals(user.getId()) && !user.getRole().name().equals("ADMIN")) {
                throw new PermissionDeniedException("You are not authorized to delete this content");
            }
        }
        // If creator is null, we assume it's an internal call or already authorized (like from AdminContentController)
        contentUnlockRepository.deleteByContentId(id);
        contentRepository.delete(content);
    }

    @Override
    @Transactional
    @CacheEvict(value = "publicContent", allEntries = true)
    public void deleteContent(UUID contentId, Long creatorId) {
        Content content = getContentById(contentId);
        
        if (!content.getCreator().getId().equals(creatorId)) {
            throw new AccessDeniedException("You are not authorized to delete this content");
        }
        
        contentUnlockRepository.deleteByContentId(contentId);
        contentRepository.delete(content);
    }

    @Transactional
    @CacheEvict(value = "publicContent", allEntries = true)
    public void disableContent(UUID id) {
        Content content = getContentById(id);
        content.setDisabled(true);
        contentRepository.save(content);
    }
}
