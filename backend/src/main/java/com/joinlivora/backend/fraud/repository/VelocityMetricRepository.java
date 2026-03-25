package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.VelocityActionType;
import com.joinlivora.backend.fraud.model.VelocityMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VelocityMetricRepository extends JpaRepository<VelocityMetric, UUID> {
    
    Optional<VelocityMetric> findByUserIdAndActionTypeAndWindowStartAndWindowEnd(
            Long userId, VelocityActionType actionType, Instant windowStart, Instant windowEnd);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO velocity_metrics (id, user_id, action_type, count, window_start, window_end) " +
            "VALUES (:id, :userId, :actionType, 1, :windowStart, :windowEnd) " +
            "ON CONFLICT (user_id, action_type, window_start, window_end) " +
            "DO UPDATE SET count = velocity_metrics.count + 1", nativeQuery = true)
    void upsertIncrement(
            @Param("id") UUID id,
            @Param("userId") Long userId,
            @Param("actionType") String actionType,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);

    List<VelocityMetric> findAllByUserIdAndWindowEndAfter(Long userId, Instant windowEnd);
}
