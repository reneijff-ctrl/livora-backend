package com.joinlivora.backend.monetization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreatorCollusionRecordRepository extends JpaRepository<CreatorCollusionRecord, UUID> {
    Page<CreatorCollusionRecord> findAllByCreatorIdOrderByEvaluatedAtDesc(UUID creatorId, Pageable pageable);
}
