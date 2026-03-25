package com.joinlivora.backend.abuse.repository;

import com.joinlivora.backend.abuse.model.AbuseEvent;
import com.joinlivora.backend.abuse.model.AbuseEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class AbuseEventRepositoryTest {

    @Autowired
    private AbuseEventRepository repository;

    @Test
    void countByUserIdAndEventTypeAndCreatedAtAfter_ShouldReturnCorrectCount() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant tenMinutesAgo = now.minus(10, ChronoUnit.MINUTES);

        repository.save(AbuseEvent.builder()
                .userId(userId)
                .eventType(AbuseEventType.RAPID_TIPPING)
                .createdAt(now.minus(5, ChronoUnit.MINUTES))
                .build());

        repository.save(AbuseEvent.builder()
                .userId(userId)
                .eventType(AbuseEventType.RAPID_TIPPING)
                .createdAt(now.minus(1, ChronoUnit.MINUTES))
                .build());

        repository.save(AbuseEvent.builder()
                .userId(userId)
                .eventType(AbuseEventType.MESSAGE_SPAM) // Different type
                .createdAt(now.minus(2, ChronoUnit.MINUTES))
                .build());

        repository.save(AbuseEvent.builder()
                .userId(userId)
                .eventType(AbuseEventType.RAPID_TIPPING)
                .createdAt(now.minus(15, ChronoUnit.MINUTES)) // Too old
                .build());

        long count = repository.countByUserIdAndEventTypeAndCreatedAtAfter(userId, AbuseEventType.RAPID_TIPPING, tenMinutesAgo);

        assertEquals(2, count);
    }

    @Test
    void countByIpAddressAndEventTypeAndCreatedAtAfter_ShouldReturnCorrectCount() {
        String ip = "192.168.1.1";
        Instant now = Instant.now();
        Instant tenMinutesAgo = now.minus(10, ChronoUnit.MINUTES);

        repository.save(AbuseEvent.builder()
                .userId(UUID.randomUUID())
                .ipAddress(ip)
                .eventType(AbuseEventType.LOGIN_BRUTE_FORCE)
                .createdAt(now.minus(5, ChronoUnit.MINUTES))
                .build());

        repository.save(AbuseEvent.builder()
                .userId(UUID.randomUUID())
                .ipAddress(ip)
                .eventType(AbuseEventType.LOGIN_BRUTE_FORCE)
                .createdAt(now.minus(1, ChronoUnit.MINUTES))
                .build());

        repository.save(AbuseEvent.builder()
                .userId(UUID.randomUUID())
                .ipAddress("8.8.8.8") // Different IP
                .eventType(AbuseEventType.LOGIN_BRUTE_FORCE)
                .createdAt(now.minus(2, ChronoUnit.MINUTES))
                .build());

        long count = repository.countByIpAddressAndEventTypeAndCreatedAtAfter(ip, AbuseEventType.LOGIN_BRUTE_FORCE, tenMinutesAgo);

        assertEquals(2, count);
    }

    @Test
    void save_WithNullUserId_ShouldSucceed() {
        AbuseEvent event = AbuseEvent.builder()
                .userId(null)
                .ipAddress("127.0.0.1")
                .eventType(AbuseEventType.SUSPICIOUS_API_USAGE)
                .description("No creator ID")
                .build();
        
        repository.save(event);
        
        long count = repository.countByIpAddressAndEventTypeAndCreatedAtAfter("127.0.0.1", AbuseEventType.SUSPICIOUS_API_USAGE, Instant.now().minus(1, ChronoUnit.HOURS));
        assertEquals(1, count);
    }
}








