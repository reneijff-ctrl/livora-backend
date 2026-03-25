package com.joinlivora.backend.chat;

import com.joinlivora.backend.monetization.PpvContent;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class PPVChatAccessRepositoryTest {

    @Autowired
    private PPVChatAccessRepository ppvChatAccessRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void testSaveAndLoad() {
        User user = TestUserFactory.createViewer("viewer@test.com");
        entityManager.persist(user);

        User creator = TestUserFactory.createCreator("creator@test.com");
        entityManager.persist(creator);

        Stream room = Stream.builder()
                .creator(creator)
                .isLive(true)
                .build();
        entityManager.persist(room);

        PpvContent ppv = PpvContent.builder()
                .creator(creator)
                .title("PPV Title")
                .price(BigDecimal.TEN)
                .currency("EUR")
                .contentUrl("http://test.com")
                .build();
        entityManager.persist(ppv);

        Instant now = Instant.now();
        PPVChatAccess access = PPVChatAccess.builder()
                .userId(user)
                .roomId(room)
                .ppvContentId(ppv)
                .grantedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        PPVChatAccess saved = ppvChatAccessRepository.save(access);
        entityManager.flush();
        entityManager.clear();

        PPVChatAccess loaded = ppvChatAccessRepository.findById(saved.getId()).orElseThrow();
        assertEquals(user.getId(), loaded.getUserId().getId());
        assertEquals(room.getId(), loaded.getRoomId().getId());
        assertEquals(ppv.getId(), loaded.getPpvContentId().getId());
        assertNotNull(loaded.getGrantedAt());
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

        PpvContent ppv = PpvContent.builder()
                .creator(creator)
                .title("PPV Title")
                .price(BigDecimal.TEN)
                .currency("EUR")
                .contentUrl("http://test.com")
                .build();
        entityManager.persist(ppv);

        PPVChatAccess access1 = PPVChatAccess.builder()
                .userId(user)
                .roomId(room)
                .ppvContentId(ppv)
                .build();
        ppvChatAccessRepository.save(access1);
        entityManager.flush();

        PPVChatAccess access2 = PPVChatAccess.builder()
                .userId(user)
                .roomId(room)
                .ppvContentId(ppv)
                .build();

        assertThrows(Exception.class, () -> {
            ppvChatAccessRepository.save(access2);
            entityManager.flush();
        });
    }

    @Test
    void testExistsActiveAccess() {
        User user = TestUserFactory.createViewer("user3@test.com");
        entityManager.persist(user);

        User creator = TestUserFactory.createCreator("creator3@test.com");
        entityManager.persist(creator);

        Stream room = Stream.builder()
                .creator(creator)
                .isLive(true)
                .build();
        entityManager.persist(room);

        PpvContent ppv = PpvContent.builder()
                .creator(creator)
                .title("PPV Title")
                .price(BigDecimal.TEN)
                .currency("EUR")
                .contentUrl("http://test.com")
                .build();
        entityManager.persist(ppv);

        Instant now = Instant.now();

        // 1. Permanent access (expiresAt is null)
        PPVChatAccess permanentAccess = PPVChatAccess.builder()
                .userId(user)
                .roomId(room)
                .ppvContentId(ppv)
                .expiresAt(null)
                .build();
        ppvChatAccessRepository.save(permanentAccess);
        entityManager.flush();

        assertTrue(ppvChatAccessRepository.existsActiveAccess(user.getId(), room.getId(), now));

        // 2. Expired access
        ppvChatAccessRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();

        PPVChatAccess expiredAccess = PPVChatAccess.builder()
                .userId(user)
                .roomId(room)
                .ppvContentId(ppv)
                .expiresAt(now.minusSeconds(60))
                .build();
        ppvChatAccessRepository.save(expiredAccess);
        entityManager.flush();

        assertFalse(ppvChatAccessRepository.existsActiveAccess(user.getId(), room.getId(), now));

        // 3. Active timed access
        ppvChatAccessRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();

        PPVChatAccess timedAccess = PPVChatAccess.builder()
                .userId(user)
                .roomId(room)
                .ppvContentId(ppv)
                .expiresAt(now.plusSeconds(60))
                .build();
        ppvChatAccessRepository.save(timedAccess);
        entityManager.flush();

        assertTrue(ppvChatAccessRepository.existsActiveAccess(user.getId(), room.getId(), now));
    }

    @Test
    void testFindByUserAndRoom() {
        User user = TestUserFactory.createViewer("user4@test.com");
        entityManager.persist(user);

        User creator = TestUserFactory.createCreator("creator4@test.com");
        entityManager.persist(creator);

        Stream room = Stream.builder()
                .creator(creator)
                .isLive(true)
                .build();
        entityManager.persist(room);

        PpvContent ppv1 = PpvContent.builder()
                .creator(creator)
                .title("PPV 1")
                .price(BigDecimal.TEN)
                .currency("EUR")
                .contentUrl("http://test.com/1")
                .build();
        entityManager.persist(ppv1);

        PpvContent ppv2 = PpvContent.builder()
                .creator(creator)
                .title("PPV 2")
                .price(BigDecimal.valueOf(20))
                .currency("EUR")
                .contentUrl("http://test.com/2")
                .build();
        entityManager.persist(ppv2);

        PPVChatAccess access1 = PPVChatAccess.builder()
                .userId(user)
                .roomId(room)
                .ppvContentId(ppv1)
                .build();
        PPVChatAccess access2 = PPVChatAccess.builder()
                .userId(user)
                .roomId(room)
                .ppvContentId(ppv2)
                .build();

        ppvChatAccessRepository.save(access1);
        ppvChatAccessRepository.save(access2);
        entityManager.flush();

        java.util.List<PPVChatAccess> results = ppvChatAccessRepository.findByUserAndRoom(user.getId(), room.getId());
        assertEquals(2, results.size());
    }

    @Test
    void testDeleteExpired() {
        User user = TestUserFactory.createViewer("user5@test.com");
        entityManager.persist(user);

        User creator = TestUserFactory.createCreator("creator5@test.com");
        entityManager.persist(creator);

        Stream room = Stream.builder()
                .creator(creator)
                .isLive(true)
                .build();
        entityManager.persist(room);

        PpvContent ppv = PpvContent.builder()
                .creator(creator)
                .title("PPV Title")
                .price(BigDecimal.TEN)
                .currency("EUR")
                .contentUrl("http://test.com")
                .build();
        entityManager.persist(ppv);

        Instant now = Instant.now();
        
        // Active access
        PPVChatAccess active = PPVChatAccess.builder()
                .userId(user)
                .roomId(room)
                .ppvContentId(ppv)
                .expiresAt(now.plusSeconds(3600))
                .build();
        
        // Expired access
        User user2 = TestUserFactory.createViewer("user6@test.com");
        entityManager.persist(user2);
        
        PPVChatAccess expired = PPVChatAccess.builder()
                .userId(user2)
                .roomId(room)
                .ppvContentId(ppv)
                .expiresAt(now.minusSeconds(3600))
                .build();

        ppvChatAccessRepository.save(active);
        ppvChatAccessRepository.save(expired);
        entityManager.flush();

        ppvChatAccessRepository.deleteExpired(now);
        entityManager.flush();
        entityManager.clear();

        assertTrue(ppvChatAccessRepository.findById(active.getId()).isPresent());
        assertFalse(ppvChatAccessRepository.findById(expired.getId()).isPresent());
    }
}








