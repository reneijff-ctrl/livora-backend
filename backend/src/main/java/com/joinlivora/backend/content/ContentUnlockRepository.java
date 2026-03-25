package com.joinlivora.backend.content;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentUnlockRepository extends JpaRepository<ContentUnlock, UUID> {
    Optional<ContentUnlock> findByUserAndContent(User user, Content content);
    boolean existsByUserAndContent(User user, Content content);
    boolean existsByUserIdAndContentId(Long userId, java.util.UUID contentId);
    void deleteByContentId(UUID contentId);
}
