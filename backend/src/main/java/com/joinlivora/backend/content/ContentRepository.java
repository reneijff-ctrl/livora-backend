package com.joinlivora.backend.content;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContentRepository extends JpaRepository<Content, UUID> {
    List<Content> findByDisabledFalse();
    List<Content> findByAccessLevelAndDisabledFalse(AccessLevel accessLevel);
    @EntityGraph(attributePaths = "creator")
    List<Content> findByAccessLevelAndDisabledFalseAndCreatorShadowbannedFalse(AccessLevel accessLevel);
    List<Content> findByCreator_Id(Long creatorId);
    @EntityGraph(attributePaths = "creator")
    List<Content> findByCreatorId(Long creatorId);
    @EntityGraph(attributePaths = "creator")
    List<Content> findByCreatorIdOrderByCreatedAtDesc(Long id);
    List<Content> findByAccessLevelInAndDisabledFalse(List<AccessLevel> accessLevels);
    @EntityGraph(attributePaths = "creator")
    List<Content> findByAccessLevelInAndDisabledFalseAndCreatorShadowbannedFalse(List<AccessLevel> accessLevels);

    @EntityGraph(attributePaths = "creator")
    Page<Content> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "creator")
    default Page<Content> findAllWithCreator(Pageable pageable) {
        return findAll(pageable);
    }

    long countByCreator(com.joinlivora.backend.user.User creator);
}
