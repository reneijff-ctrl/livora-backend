package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.FraudEvent;
import com.joinlivora.backend.fraud.model.FraudEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface FraudEventRepository extends JpaRepository<FraudEvent, UUID> {
    List<FraudEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<FraudEvent> findByEventType(FraudEventType type);

    /**
     * Counts the number of fraud enforcement events created after a given timestamp.
     *
     * @param since the starting timestamp
     * @return the count of events
     */
    long countByCreatedAtAfter(Instant since);
}
