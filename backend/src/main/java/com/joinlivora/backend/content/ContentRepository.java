package com.joinlivora.backend.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContentRepository extends JpaRepository<Content, UUID> {
    List<Content> findByDisabledFalse();
    List<Content> findByAccessLevelAndDisabledFalse(ContentAccessLevel accessLevel);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "creator")
    List<Content> findByAccessLevelAndDisabledFalseAndCreatorShadowbannedFalse(ContentAccessLevel accessLevel);
    List<Content> findByCreator_Id(Long creatorId);
    List<Content> findByAccessLevelInAndDisabledFalse(List<ContentAccessLevel> accessLevels);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "creator")
    List<Content> findByAccessLevelInAndDisabledFalseAndCreatorShadowbannedFalse(List<ContentAccessLevel> accessLevels);

    long countByCreator(com.joinlivora.backend.user.User creator);
}
