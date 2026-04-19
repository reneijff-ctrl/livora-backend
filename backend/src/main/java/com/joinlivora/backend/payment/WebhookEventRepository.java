package com.joinlivora.backend.payment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    boolean existsByStripeEventId(String stripeEventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WebhookEvent w WHERE w.id = :id")
    Optional<WebhookEvent> findByIdWithLock(@Param("id") UUID id);

    @Query("SELECT w FROM WebhookEvent w WHERE w.processed = false AND w.errorMessage IS NOT NULL " +
           "AND w.createdAt >= :since AND w.retryCount < :maxRetries ORDER BY w.createdAt ASC")
    List<WebhookEvent> findFailedEventsForRetry(@Param("since") Instant since,
                                                @Param("maxRetries") int maxRetries,
                                                Pageable pageable);
}
