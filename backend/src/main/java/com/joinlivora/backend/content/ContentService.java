package com.joinlivora.backend.content;

import com.joinlivora.backend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface ContentService {
    List<Content> getPublicContent();
    List<Content> getFeedContent(User user, boolean hasPremium);
    List<Content> getPremiumContent();
    List<Content> getCreatorContent(User creator);
    List<Content> getContentByCreatorId(Long creatorId);
    Content getContentById(UUID id);
    Content createContent(Content content);
    Content updateContent(UUID id, Content contentDetails, User creator);
    Content updateContent(UUID contentId, Long creatorId, com.joinlivora.backend.content.dto.UpdateContentRequest request);
    void deleteContent(UUID id, User user);
    void deleteContent(UUID contentId, Long creatorId);
    void disableContent(UUID id);
}
