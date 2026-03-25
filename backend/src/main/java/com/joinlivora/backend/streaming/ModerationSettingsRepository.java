package com.joinlivora.backend.streaming;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModerationSettingsRepository extends JpaRepository<ModerationSettings, Long> {
    Optional<ModerationSettings> findByCreatorUserId(Long creatorUserId);
}
