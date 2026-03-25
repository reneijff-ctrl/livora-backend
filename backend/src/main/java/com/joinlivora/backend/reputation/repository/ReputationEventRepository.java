package com.joinlivora.backend.reputation.repository;

import com.joinlivora.backend.reputation.model.ReputationEvent;
import com.joinlivora.backend.reputation.model.ReputationEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReputationEventRepository extends JpaRepository<ReputationEvent, UUID> {
    List<ReputationEvent> findAllByCreatorIdOrderByCreatedAtDesc(UUID creatorId);

    Optional<ReputationEvent> findFirstByCreatorIdAndTypeOrderByCreatedAtDesc(UUID creatorId, ReputationEventType type);

    boolean existsByCreatorIdAndTypeInAndCreatedAtAfter(UUID creatorId, Collection<ReputationEventType> types, Instant after);

    long countByCreatorIdAndTypeAndCreatedAtAfter(UUID creatorId, ReputationEventType type, Instant after);
}
