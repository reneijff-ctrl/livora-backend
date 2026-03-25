package com.joinlivora.backend.reputation.repository;

import com.joinlivora.backend.reputation.model.CreatorReputationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreatorReputationSnapshotRepository extends JpaRepository<CreatorReputationSnapshot, UUID> {
    List<CreatorReputationSnapshot> findAllByCurrentScoreGreaterThan(int score);
    List<CreatorReputationSnapshot> findAllByCurrentScoreLessThan(int score);
}
