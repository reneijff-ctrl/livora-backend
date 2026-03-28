package com.joinlivora.backend.privateshow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreatorPrivateSettingsRepository extends JpaRepository<CreatorPrivateSettings, Long> {
}
