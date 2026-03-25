package com.joinlivora.backend.streaming.dto;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModerationSettingsRequestTest {

    @Test
    void setBannedWords_WithList() {
        ModerationSettingsRequest request = new ModerationSettingsRequest();
        request.setBannedWords(List.of("word1", "word2"));
        assertEquals(List.of("word1", "word2"), request.getBannedWords());
    }

    @Test
    void setBannedWords_WithString() {
        ModerationSettingsRequest request = new ModerationSettingsRequest();
        request.setBannedWords("word1, word2, word3");
        assertEquals(List.of("word1", "word2", "word3"), request.getBannedWords());
    }

    @Test
    void setBannedWords_WithEmptyString() {
        ModerationSettingsRequest request = new ModerationSettingsRequest();
        request.setBannedWords("");
        assertTrue(request.getBannedWords().isEmpty());
    }

    @Test
    void setBannedWords_WithBlankString() {
        ModerationSettingsRequest request = new ModerationSettingsRequest();
        request.setBannedWords("   ");
        assertTrue(request.getBannedWords().isEmpty());
    }

    @Test
    void setBannedWords_WithNull() {
        ModerationSettingsRequest request = new ModerationSettingsRequest();
        request.setBannedWords(null);
        assertNotNull(request.getBannedWords());
        assertTrue(request.getBannedWords().isEmpty());
    }

    @Test
    void setBannedWords_WithMixedList() {
        ModerationSettingsRequest request = new ModerationSettingsRequest();
        request.setBannedWords(List.of("word1", 123, true));
        assertEquals(List.of("word1", "123", "true"), request.getBannedWords());
    }
}








