package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.FraudEvent;
import com.joinlivora.backend.fraud.model.FraudEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class FraudEventRepositoryTest {

    @Autowired
    private FraudEventRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_ShouldReturnOrderedEvents() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        
        FraudEvent event1 = FraudEvent.builder()
                .userId(userId)
                .eventType(FraudEventType.CHARGEBACK_REPORTED)
                .reason("First event")
                .build();
        repository.save(event1);
        
        // Ensure different timestamps
        Thread.sleep(10);
        
        FraudEvent event2 = FraudEvent.builder()
                .userId(userId)
                .eventType(FraudEventType.ACCOUNT_SUSPENDED)
                .reason("Second event")
                .build();
        repository.save(event2);

        List<FraudEvent> events = repository.findByUserIdOrderByCreatedAtDesc(userId);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getReason()).isEqualTo("Second event");
        assertThat(events.get(1).getReason()).isEqualTo("First event");
    }

    @Test
    void findByEventType_ShouldReturnEventsOfSpecificType() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        repository.save(FraudEvent.builder()
                .userId(userId1)
                .eventType(FraudEventType.PAYOUT_FROZEN)
                .reason("Reason 1")
                .build());

        repository.save(FraudEvent.builder()
                .userId(userId2)
                .eventType(FraudEventType.PAYOUT_FROZEN)
                .reason("Reason 2")
                .build());

        repository.save(FraudEvent.builder()
                .userId(userId1)
                .eventType(FraudEventType.ACCOUNT_TERMINATED)
                .reason("Reason 3")
                .build());

        List<FraudEvent> frozenEvents = repository.findByEventType(FraudEventType.PAYOUT_FROZEN);

        assertThat(frozenEvents).hasSize(2);
        assertThat(frozenEvents).allMatch(e -> e.getEventType() == FraudEventType.PAYOUT_FROZEN);
    }

    @Test
    void countByCreatedAtAfter_ShouldReturnCorrectCount() {
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(Duration.ofHours(1));
        Instant twoHoursAgo = now.minus(Duration.ofHours(2));
        Instant threeHoursAgo = now.minus(Duration.ofHours(3));

        // Event from 3 hours ago
        repository.save(FraudEvent.builder()
                .userId(UUID.randomUUID())
                .eventType(FraudEventType.CHARGEBACK_REPORTED)
                .reason("oldest")
                .createdAt(threeHoursAgo)
                .build());

        // Event from 2 hours ago
        repository.save(FraudEvent.builder()
                .userId(UUID.randomUUID())
                .eventType(FraudEventType.CHARGEBACK_REPORTED)
                .reason("middle")
                .createdAt(twoHoursAgo)
                .build());

        // Event from 1 hour ago
        repository.save(FraudEvent.builder()
                .userId(UUID.randomUUID())
                .eventType(FraudEventType.CHARGEBACK_REPORTED)
                .reason("newest")
                .createdAt(oneHourAgo)
                .build());

        // Count after 2.5 hours ago -> should find 2 events (middle and newest)
        long count = repository.countByCreatedAtAfter(now.minus(Duration.ofMinutes(150)));
        assertThat(count).isEqualTo(2);

        // Count after 30 mins ago -> should find 0
        long countRecent = repository.countByCreatedAtAfter(now.minus(Duration.ofMinutes(30)));
        assertThat(countRecent).isEqualTo(0);
    }
}








