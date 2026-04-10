package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.monetization.PpvContent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatRoomTest {

    @Test
    void testPpvProperties_WithPpvContent_ShouldReturnTrue() {
        PpvContent ppv = PpvContent.builder().id(UUID.randomUUID()).build();
        ChatRoom room = ChatRoom.builder()
                .ppvContent(ppv)
                .build();

        assertNotNull(room.getPpvContent());
    }

    @Test
    void testPpvProperties_WithoutPpvContent_ShouldReturnFalse() {
        ChatRoom room = ChatRoom.builder()
                .build();

        assertNull(room.getPpvContent());
    }

    @Test
    void testPpvProperties_WithPpvContentSet_ShouldReturnTrue() {
        PpvContent ppv = PpvContent.builder().id(UUID.randomUUID()).build();
        ChatRoom room = new ChatRoom();
        room.setPpvContent(ppv);

        assertNotNull(room.getPpvContent());
        assertEquals(ppv.getId(), room.getPpvContent().getId());
    }

    @Test
    void testChatMode_DefaultValue_ShouldBePublic() {
        ChatRoom room = new ChatRoom();
        assertEquals(ChatMode.PUBLIC, room.getChatMode());
    }

    @Test
    void testChatMode_BuilderDefaultValue_ShouldBePublic() {
        ChatRoom room = ChatRoom.builder().build();
        assertEquals(ChatMode.PUBLIC, room.getChatMode());
    }

    @Test
    void testChatMode_SettingValue_ShouldWork() {
        ChatRoom room = ChatRoom.builder()
                .chatMode(ChatMode.SUBSCRIBERS_ONLY)
                .build();
        assertEquals(ChatMode.SUBSCRIBERS_ONLY, room.getChatMode());
    }
}
