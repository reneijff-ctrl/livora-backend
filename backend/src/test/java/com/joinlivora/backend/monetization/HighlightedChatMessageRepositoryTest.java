package com.joinlivora.backend.monetization;

import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Legacy streaming architecture")
@DataJpaTest
@ActiveProfiles("test")
class HighlightedChatMessageRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HighlightedChatMessageRepository repository;

    private User creator;
    private User viewer;
    private Stream room;

    @BeforeEach
    void setUp() {
        creator = TestUserFactory.createCreator("creator-" + System.nanoTime() + "@test.com");
        viewer = TestUserFactory.createViewer("viewer-" + System.nanoTime() + "@test.com");
        entityManager.persist(creator);
        entityManager.persist(viewer);

        room = Stream.builder()
                .creator(creator)
                .isLive(true)
                .build();
        entityManager.persist(room);
        entityManager.flush();
    }

    @Test
    void findByRoomIdOrderByCreatedAtDesc_ShouldReturnOrderedMessages() {
        HighlightedMessage msg1 = createMessage("msg1", Instant.now().minus(1, ChronoUnit.HOURS));
        HighlightedMessage msg2 = createMessage("msg2", Instant.now());
        
        entityManager.persist(msg1);
        entityManager.persist(msg2);
        entityManager.flush();

        List<HighlightedMessage> result = repository.findByRoomIdOrderByCreatedAtDesc(room);

        assertEquals(2, result.size());
        assertEquals("msg2", result.get(0).getMessageId());
        assertEquals("msg1", result.get(1).getMessageId());
    }

    @Test
    void sumAmountByCreatorAndPeriod_ShouldSumCorrectly() {
        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        
        HighlightedMessage oldMsg = createMessage("old", since.minus(1, ChronoUnit.HOURS));
        oldMsg.setAmount(new BigDecimal("10.00"));
        
        HighlightedMessage newMsg1 = createMessage("new1", since.plus(1, ChronoUnit.HOURS));
        newMsg1.setAmount(new BigDecimal("20.00"));
        
        HighlightedMessage newMsg2 = createMessage("new2", since.plus(2, ChronoUnit.HOURS));
        newMsg2.setAmount(new BigDecimal("30.50"));

        entityManager.persist(oldMsg);
        entityManager.persist(newMsg1);
        entityManager.persist(newMsg2);
        entityManager.flush();

        BigDecimal sum = repository.sumAmountByCreatorAndPeriod(creator, since);

        assertNotNull(sum);
        assertEquals(0, new BigDecimal("50.50").compareTo(sum));
    }

    @Test
    void sumAmountByCreatorAndPeriod_ShouldReturnNullWhenNoMatches() {
        BigDecimal sum = repository.sumAmountByCreatorAndPeriod(creator, Instant.now());
        assertNull(sum);
    }

    @Test
    void testFindTopStreamsByCreator() {
        // We can only have one room per creator due to @OneToOne
        // So we test that we get the one room for our creator, and not from another creator
        User otherCreator = TestUserFactory.createCreator("other-" + System.nanoTime() + "@test.com");
        entityManager.persist(otherCreator);
        
        Stream otherRoom = Stream.builder()
                .creator(otherCreator)
                .title("Other Stream")
                .isLive(false)
                .build();
        entityManager.persist(otherRoom);

        HighlightedMessage msg1 = createMessage("msg1", Instant.now());
        msg1.setRoomId(room);
        msg1.setAmount(new BigDecimal("100.00"));

        HighlightedMessage msg2 = createMessage("msg2", Instant.now());
        msg2.setRoomId(otherRoom);
        msg2.setAmount(new BigDecimal("50.00"));

        entityManager.persist(msg1);
        entityManager.persist(msg2);
        entityManager.flush();

        List<Object[]> results = repository.findTopStreamsByCreator(creator, PageRequest.of(0, 10));

        assertEquals(1, results.size());
        assertEquals(room.getId(), results.get(0)[0]);
        assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) results.get(0)[2]));
    }

    private HighlightedMessage createMessage(String messageId, Instant createdAt) {
        return HighlightedMessage.builder()
                .messageId(messageId)
                .content("Test content")
                .userId(viewer)
                .roomId(room)
                .amount(BigDecimal.TEN)
                .currency("TOKEN")
                .highlightType(HighlightType.COLOR)
                .status(TipStatus.COMPLETED)
                .createdAt(createdAt)
                .build();
    }
}











