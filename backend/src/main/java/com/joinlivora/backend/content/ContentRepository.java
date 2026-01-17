package com.joinlivora.backend.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContentRepository extends JpaRepository<Content, UUID> {
    List<Content> findByDisabledFalse();
    List<Content> findByAccessLevelAndDisabledFalse(ContentAccessLevel accessLevel);
    List<Content> findByCreatorId(Long creatorId);
    List<Content> findByAccessLevelInAndDisabledFalse(List<ContentAccessLevel> accessLevels);
}
