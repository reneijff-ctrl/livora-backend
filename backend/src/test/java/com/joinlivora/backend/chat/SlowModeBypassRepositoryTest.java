package com.joinlivora.backend.chat;

import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class SlowModeBypassRepositoryTest {

    @Autowired
    private SlowModeBypassRepository slowModeBypassRepository;

    @Autowired
    private TestEntityManager entityManager;

    @org.junit.jupiter.api.BeforeEach
    void clearDatabase() {
        slowModeBypassRepository.deleteAll();
    }

    @Test
    void testSaveAndLoad() {
        User user = TestUserFactory.createViewer("user1@test.com");
        entityManager.persist(user);

        User creator = TestUserFactory.createCreator("creator1@test.com");
        entityManager.persist(creator);

        Stream room = Stream.builder()
                .creator(creator)
                .isLive(true)
                .build();
        entityManager.persist(room);

        Instant expiry = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        SlowModeBypass bypass = SlowModeBypass.builder()
                .userId(user)
                .roomId(room)
                .expiresAt(expiry)
                .source(SlowModeBypassSource.SUPERTIP)
                .build();

        SlowModeBypass saved = slowModeBypassRepository.save(bypass);
        entityManager.flush();
        entityManager.clear();

        SlowModeBypass loaded = slowModeBypassRepository.findById(saved.getId()).orElseThrow();
        assertEquals(user.getId(), loaded.getUserId().getId());
        assertEquals(room.getId(), loaded.getRoomId().getId());
        assertEquals(SlowModeBypassSource.SUPERTIP, loaded.getSource());
        assertEquals(expiry, loaded.getExpiresAt());
        assertNotNull(loaded.getCreatedAt());
    }

    @Test
    void testUniqueConstraint() {
        User user = TestUserFactory.createViewer("user2@test.com");
        entityManager.persist(user);

        User creator = TestUserFactory.createCreator("creator2@test.com");
        entityManager.persist(creator);

        Stream room = Stream.builder()
                .creator(creator)
                .isLive(true)
                .build();
        entityManager.persist(room);

        SlowModeBypass bypass1 = SlowModeBypass.builder()
                .userId(user)
                .roomId(room)
                .expiresAt(Instant.now().plusSeconds(3600))
                .source(SlowModeBypassSource.SUPERTIP)
                .build();
        slowModeBypassRepository.save(bypass1);
        entityManager.flush();

        SlowModeBypass bypass2 = SlowModeBypass.builder()
                .userId(user)
                .roomId(room)
                .expiresAt(Instant.now().plusSeconds(7200))
                .source(SlowModeBypassSource.PPV)
                .build();

        assertThrows(Exception.class, () -> {
            slowModeBypassRepository.save(bypass2);
            entityManager.flush();
        });
    }

    @Test
    void testFindActiveByUserIdAndRoomId() {
        User user = TestUserFactory.createViewer("user3@test.com");
        entityManager.persist(user);

        User creator = TestUserFactory.createCreator("creator3@test.com");
        entityManager.persist(creator);

        Stream room = Stream.builder()
                .creator(creator)
                .isLive(true)
                .build();
        entityManager.persist(room);

        Instant now = Instant.now();
        SlowModeBypass bypass = SlowModeBypass.builder()
                .userId(user)
                .roomId(room)
                .expiresAt(now.plusSeconds(3600))
                .source(SlowModeBypassSource.SUPERTIP)
                .build();
        slowModeBypassRepository.save(bypass);
        entityManager.flush();

        // Should find active
        Optional<SlowModeBypass> found = slowModeBypassRepository.findActiveByUserIdAndRoomId(user.getId(), room.getId(), now);
        assertTrue(found.isPresent());
        assertEquals(bypass.getId(), found.get().getId());

        // Should not find if expired
        found = slowModeBypassRepository.findActiveByUserIdAndRoomId(user.getId(), room.getId(), now.plusSeconds(7200));
        assertFalse(found.isPresent());

        // Should not find for different creator
        found = slowModeBypassRepository.findActiveByUserIdAndRoomId(999L, room.getId(), now);
        assertFalse(found.isPresent());
    }

    @Test
    void testDeleteExpired() {
        User user = TestUserFactory.createViewer("user4@test.com");
        entityManager.persist(user);

        User creator = TestUserFactory.createCreator("creator4@test.com");
        entityManager.persist(creator);

        Stream room = Stream.builder()
                .creator(creator)
                .isLive(true)
                .build();
        entityManager.persist(room);

        Instant now = Instant.now();
        
        // Active bypass
        SlowModeBypass active = SlowModeBypass.builder()
                .userId(user)
                .roomId(room)
                .expiresAt(now.plusSeconds(3600))
                .source(SlowModeBypassSource.SUPERTIP)
                .build();
        
        // Expired bypass (needs another creator/room combo or just use the unique constraint)
        User user2 = TestUserFactory.createViewer("user5@test.com");
        entityManager.persist(user2);
        
        SlowModeBypass expired = SlowModeBypass.builder()
                .userId(user2)
                .roomId(room)
                .expiresAt(now.minusSeconds(3600))
                .source(SlowModeBypassSource.SUPERTIP)
                .build();

        slowModeBypassRepository.save(active);
        slowModeBypassRepository.save(expired);
        entityManager.flush();

        assertEquals(2, slowModeBypassRepository.count());

        slowModeBypassRepository.deleteExpired(now);
        entityManager.flush();
        entityManager.clear();

        assertEquals(1, slowModeBypassRepository.count());
        assertTrue(slowModeBypassRepository.findById(active.getId()).isPresent());
        assertFalse(slowModeBypassRepository.findById(expired.getId()).isPresent());
    }
}








