package com.joinlivora.backend.monetization;

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

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class TipTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void tip_Persistence_ShouldWork() {
        User sender = TestUserFactory.createViewer("sender@test.com");
        User creator = TestUserFactory.createCreator("creator@test.com");
        entityManager.persist(sender);
        entityManager.persist(creator);

        Stream room = Stream.builder()
                .creator(creator)
                .isLive(true)
                .build();
        entityManager.persist(room);

        Tip tip = Tip.builder()
                .senderUserId(sender)
                .creatorUserId(creator)
                .room(room)
                .amount(new BigDecimal("10.00"))
                .currency("TOKEN")
                .message("Great liveStream!")
                .status(TipStatus.COMPLETED)
                .build();

        Tip saved = entityManager.persistFlushFind(tip);

        assertNotNull(saved.getId());
        assertEquals(sender.getId(), saved.getSenderUserId().getId());
        assertEquals(creator.getId(), saved.getCreatorUserId().getId());
        assertEquals(room.getId(), saved.getRoom().getId());
        assertEquals(0, new BigDecimal("10.00").compareTo(saved.getAmount()));
        assertEquals("TOKEN", saved.getCurrency());
        assertEquals("Great liveStream!", saved.getMessage());
        assertEquals(TipStatus.COMPLETED, saved.getStatus());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void tip_WithoutRoom_ShouldWork() {
        User sender = TestUserFactory.createViewer("sender2@test.com");
        User creator = TestUserFactory.createCreator("creator2@test.com");
        entityManager.persist(sender);
        entityManager.persist(creator);

        Tip tip = Tip.builder()
                .senderUserId(sender)
                .creatorUserId(creator)
                .amount(new BigDecimal("5.00"))
                .currency("EUR")
                .status(TipStatus.PENDING)
                .build();

        Tip saved = entityManager.persistFlushFind(tip);

        assertNotNull(saved.getId());
        assertNull(saved.getRoom());
        assertEquals(TipStatus.PENDING, saved.getStatus());
    }
}










