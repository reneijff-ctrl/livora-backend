package com.joinlivora.backend.chat.dto;

import com.joinlivora.backend.chat.ChatMode;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.monetization.PpvContent;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatRoomResponseTest {

    @Test
    void fromEntity_WithRoomOwner_ShouldSetFlagsTrue() {
        User owner = new User();
        owner.setId(1L);
        owner.setRole(Role.CREATOR);

        Long roomId = 101L;
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .creatorId(owner.getId())
                .chatMode(ChatMode.PUBLIC)
                .build();

        ChatRoomResponse response = ChatRoomResponse.fromEntity(room, owner);

        assertTrue(response.isCanChangeChatMode());
        assertTrue(response.isCanBypassChatRestrictions());
        assertEquals(new UUID(0, roomId), response.getId());
    }

    @Test
    void fromEntity_WithAdmin_ShouldSetFlagsTrue() {
        User owner = new User();
        owner.setId(1L);

        User admin = new User();
        admin.setId(2L);
        admin.setRole(Role.ADMIN);

        Long roomId = 102L;
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .creatorId(owner.getId())
                .build();

        ChatRoomResponse response = ChatRoomResponse.fromEntity(room, admin);

        assertTrue(response.isCanChangeChatMode());
        assertTrue(response.isCanBypassChatRestrictions());
    }

    @Test
    void fromEntity_WithModerator_ShouldSetFlagsTrue() {
        User owner = new User();
        owner.setId(1L);

        User moderator = new User();
        moderator.setId(3L);
        moderator.setRole(Role.MODERATOR);

        Long roomId = 103L;
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .creatorId(owner.getId())
                .build();

        ChatRoomResponse response = ChatRoomResponse.fromEntity(room, moderator);

        assertTrue(response.isCanChangeChatMode());
        assertTrue(response.isCanBypassChatRestrictions());
    }

    @Test
    void fromEntity_WithRegularUser_ShouldSetFlagsFalse() {
        User owner = new User();
        owner.setId(1L);

        User user = new User();
        user.setId(4L);
        user.setRole(Role.USER);

        Long roomId = 104L;
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .creatorId(owner.getId())
                .build();

        ChatRoomResponse response = ChatRoomResponse.fromEntity(room, user);

        assertFalse(response.isCanChangeChatMode());
        assertFalse(response.isCanBypassChatRestrictions());
    }

    @Test
    void fromEntity_WithPpvCreator_ShouldSetBypassTrueButChangeModeFalse() {
        User owner = new User();
        owner.setId(1L);

        User ppvCreator = new User();
        ppvCreator.setId(5L);
        ppvCreator.setRole(Role.CREATOR);

        PpvContent ppv = PpvContent.builder()
                .id(UUID.randomUUID())
                .creator(ppvCreator)
                .build();

        Long roomId = 105L;
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .creatorId(owner.getId())
                .ppvContent(ppv)
                .build();

        ChatRoomResponse response = ChatRoomResponse.fromEntity(room, ppvCreator);

        assertFalse(response.isCanChangeChatMode());
        assertTrue(response.isCanBypassChatRestrictions());
    }

    @Test
    void fromEntity_WithNullUser_ShouldSetFlagsFalse() {
        Long roomId = 106L;
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .build();

        ChatRoomResponse response = ChatRoomResponse.fromEntity(room, null);

        assertFalse(response.isCanChangeChatMode());
        assertFalse(response.isCanBypassChatRestrictions());
    }
}









