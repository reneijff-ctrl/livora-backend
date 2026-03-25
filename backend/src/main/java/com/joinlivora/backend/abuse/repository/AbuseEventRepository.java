package com.joinlivora.backend.abuse.repository;

import com.joinlivora.backend.abuse.model.AbuseEvent;
import com.joinlivora.backend.abuse.model.AbuseEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AbuseEventRepository extends JpaRepository<AbuseEvent, UUID> {

    long countByUserIdAndEventTypeAndCreatedAtAfter(UUID userId, AbuseEventType eventType, Instant createdAtAfter);

    long countByIpAddressAndEventTypeAndCreatedAtAfter(String ipAddress, AbuseEventType eventType, Instant createdAtAfter);
}
