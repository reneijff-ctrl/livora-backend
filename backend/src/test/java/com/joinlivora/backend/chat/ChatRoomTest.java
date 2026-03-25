package com.joinlivora.backend.chat;

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

        assertTrue(room.isPpvRoom());
        assertTrue(room.isRequiresPurchase());
    }

    @Test
    void testPpvProperties_WithoutPpvContent_ShouldReturnFalse() {
        ChatRoom room = ChatRoom.builder()
                .build();

        assertFalse(room.isPpvRoom());
        assertFalse(room.isRequiresPurchase());
    }

    @Test
    void testPpvProperties_WithPpvContentId_ShouldReturnTrue() {
        UUID ppvId = UUID.randomUUID();
        ChatRoom room = new ChatRoom();
        room.setPpvContentId(ppvId);

        assertTrue(room.isPpvRoom());
        assertTrue(room.isRequiresPurchase());
        assertEquals(ppvId, room.getPpvContentId());
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








