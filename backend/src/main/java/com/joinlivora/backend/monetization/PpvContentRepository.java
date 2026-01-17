package com.joinlivora.backend.monetization;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PpvContentRepository extends JpaRepository<PpvContent, UUID> {
    List<PpvContent> findAllByCreatorAndActiveTrue(User creator);
}
