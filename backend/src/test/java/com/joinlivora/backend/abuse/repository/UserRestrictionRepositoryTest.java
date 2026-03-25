package com.joinlivora.backend.abuse.repository;

import com.joinlivora.backend.abuse.model.RestrictionLevel;
import com.joinlivora.backend.abuse.model.UserRestriction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRestrictionRepositoryTest {

    @Autowired
    private UserRestrictionRepository repository;

    @Test
    void save_ShouldSucceed() {
        UUID userId = UUID.randomUUID();
        UserRestriction restriction = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.CHAT_MUTE)
                .reason("Spamming")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        UserRestriction saved = repository.save(restriction);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals(userId, saved.getUserId());
        assertEquals(RestrictionLevel.CHAT_MUTE, saved.getRestrictionLevel());
    }

    @Test
    void save_DuplicateUserId_ShouldThrowException() {
        UUID userId = UUID.randomUUID();
        UserRestriction restriction1 = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.CHAT_MUTE)
                .reason("Reason 1")
                .build();
        repository.saveAndFlush(restriction1);

        UserRestriction restriction2 = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.SLOW_MODE)
                .reason("Reason 2")
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.saveAndFlush(restriction2);
        });
    }

    @Test
    void findByUserId_ShouldReturnRestriction() {
        UUID userId = UUID.randomUUID();
        UserRestriction restriction = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.TEMP_SUSPENSION)
                .reason("Abuse")
                .build();
        repository.save(restriction);

        var found = repository.findByUserId(userId);

        assertTrue(found.isPresent());
        assertEquals(RestrictionLevel.TEMP_SUSPENSION, found.get().getRestrictionLevel());
    }

    @Test
    void findActiveByUserId_WithActiveRestriction_ShouldReturnIt() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UserRestriction restriction = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.CHAT_MUTE)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .build();
        repository.save(restriction);

        var found = repository.findActiveByUserId(userId, now);

        assertTrue(found.isPresent());
    }

    @Test
    void findActiveByUserId_WithExpiredRestriction_ShouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UserRestriction restriction = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.CHAT_MUTE)
                .expiresAt(now.minus(1, ChronoUnit.HOURS))
                .build();
        repository.save(restriction);

        var found = repository.findActiveByUserId(userId, now);

        assertFalse(found.isPresent());
    }

    @Test
    void findActiveByUserId_WithPermanentRestriction_ShouldReturnIt() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UserRestriction restriction = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.TEMP_SUSPENSION)
                .expiresAt(null)
                .build();
        repository.save(restriction);

        var found = repository.findActiveByUserId(userId, now);

        assertTrue(found.isPresent());
    }

    @Test
    void existsActiveRestriction_ShouldReturnCorrectStatus() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID userId3 = UUID.randomUUID();
        Instant now = Instant.now();

        // Active
        repository.save(UserRestriction.builder()
                .userId(userId1)
                .restrictionLevel(RestrictionLevel.CHAT_MUTE)
                .expiresAt(now.plus(10, ChronoUnit.MINUTES))
                .build());

        // Expired
        repository.save(UserRestriction.builder()
                .userId(userId2)
                .restrictionLevel(RestrictionLevel.CHAT_MUTE)
                .expiresAt(now.minus(10, ChronoUnit.MINUTES))
                .build());

        // Permanent
        repository.save(UserRestriction.builder()
                .userId(userId3)
                .restrictionLevel(RestrictionLevel.TEMP_SUSPENSION)
                .expiresAt(null)
                .build());

        assertTrue(repository.existsActiveRestriction(userId1, now));
        assertFalse(repository.existsActiveRestriction(userId2, now));
        assertTrue(repository.existsActiveRestriction(userId3, now));
        assertFalse(repository.existsActiveRestriction(UUID.randomUUID(), now));
    }

    @Test
    void deleteByExpiresAtBefore_ShouldRemoveExpiredRestrictions() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        Instant now = Instant.now();

        repository.save(UserRestriction.builder()
                .userId(userId1)
                .restrictionLevel(RestrictionLevel.CHAT_MUTE)
                .expiresAt(now.minus(1, ChronoUnit.HOURS))
                .build());

        repository.save(UserRestriction.builder()
                .userId(userId2)
                .restrictionLevel(RestrictionLevel.CHAT_MUTE)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .build());

        repository.deleteByExpiresAtBefore(now);
        repository.flush();

        assertFalse(repository.findByUserId(userId1).isPresent());
        assertTrue(repository.findByUserId(userId2).isPresent());
    }
}








