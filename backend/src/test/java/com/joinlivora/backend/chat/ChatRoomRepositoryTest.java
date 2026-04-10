package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.monetization.PpvContent;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ChatRoomRepositoryTest {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void testFindByCreatorId() {
        User creator = TestUserFactory.createCreator("creatorUserId@test.com");
        entityManager.persist(creator);

        ChatRoom room1 = ChatRoom.builder()
                .name("Room 1")
                .creatorId(creator.getId())
                .isPrivate(false)
                .build();
        ChatRoom room2 = ChatRoom.builder()
                .name("Room 2")
                .creatorId(creator.getId())
                .isPrivate(false)
                .build();
        
        entityManager.persist(room1);
        entityManager.persist(room2);
        entityManager.flush();

        Optional<ChatRoom> found = chatRoomRepository.findByCreatorId(creator.getId());
        
        assertTrue(found.isPresent());
    }

    @Test
    void testFindByPpvContentId() {
        User creator = TestUserFactory.createCreator("creator2@test.com");
        entityManager.persist(creator);

        PpvContent ppv = PpvContent.builder()
                .creator(creator)
                .title("PPV Content")
                .price(BigDecimal.TEN)
                .currency("USD")
                .contentUrl("http://test.com/content")
                .active(true)
                .build();
        entityManager.persist(ppv);

        ChatRoom room = ChatRoom.builder()
                .name("PPV Room")
                .creatorId(creator.getId())
                .ppvContent(ppv)
                .isPrivate(true)
                .build();
        entityManager.persist(room);
        entityManager.flush();

        Optional<ChatRoom> found = chatRoomRepository.findByPpvContentId(ppv.getId());
        
        assertTrue(found.isPresent());
        assertEquals("PPV Room", found.get().getName());
        assertEquals(ppv.getId(), found.get().getPpvContent().getId());
    }

    @Test
    void testFindByPpvContentId_NotFound() {
        Optional<ChatRoom> found = chatRoomRepository.findByPpvContentId(UUID.randomUUID());
        assertFalse(found.isPresent());
    }

    @Test
    void testChatModePersistence() {
        User creator = TestUserFactory.createCreator("creator3@test.com");
        entityManager.persist(creator);

        ChatRoom room = ChatRoom.builder()
                .name("Subscriber Room")
                .creatorId(creator.getId())
                .isPrivate(true)
                .chatMode(ChatMode.SUBSCRIBERS_ONLY)
                .build();
        
        entityManager.persist(room);
        entityManager.flush();
        entityManager.clear();

        ChatRoom found = chatRoomRepository.findByName("Subscriber Room").orElseThrow();
        assertEquals(ChatMode.SUBSCRIBERS_ONLY, found.getChatMode());
    }

    @Test
    void testChatModeDefaultPersistence() {
        User creator = TestUserFactory.createCreator("creator4@test.com");
        entityManager.persist(creator);

        ChatRoom room = ChatRoom.builder()
                .name("Default Mode Room")
                .creatorId(creator.getId())
                .isPrivate(false)
                .build();
        
        entityManager.persist(room);
        entityManager.flush();
        entityManager.clear();

        ChatRoom found = chatRoomRepository.findByName("Default Mode Room").orElseThrow();
        assertEquals(ChatMode.PUBLIC, found.getChatMode());
    }

    @Test
    void testFindAllByIsLiveTrue() {
        User creator = TestUserFactory.createCreator("creator5@test.com");
        entityManager.persist(creator);

        ChatRoom liveRoom = ChatRoom.builder()
                .name("Live Room")
                .creatorId(creator.getId())
                .isLive(true)
                .build();
        ChatRoom offlineRoom = ChatRoom.builder()
                .name("Offline Room")
                .creatorId(creator.getId())
                .isLive(false)
                .build();

        entityManager.persist(liveRoom);
        entityManager.persist(offlineRoom);
        entityManager.flush();

        List<ChatRoom> liveRooms = chatRoomRepository.findAllByIsLiveTrue();
        
        assertEquals(1, liveRooms.size());
        assertEquals("Live Room", liveRooms.get(0).getName());
    }

    @Test
    void testFindById() {
        User creator = TestUserFactory.createCreator("creator6@test.com");
        entityManager.persist(creator);

        ChatRoom room = ChatRoom.builder()
                .name("ID Room")
                .creatorId(creator.getId())
                .build();
        
        entityManager.persist(room);
        entityManager.flush();
        
        Long id = room.getId();
        assertNotNull(id);

        Optional<ChatRoom> found = chatRoomRepository.findById(id);
        assertTrue(found.isPresent());
        assertEquals("ID Room", found.get().getName());
    }
}
