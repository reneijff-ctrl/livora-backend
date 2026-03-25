package com.joinlivora.backend.presence.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OnlineCreatorRegistryTest {

    private OnlineCreatorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new OnlineCreatorRegistry();
    }

    @Test
    void markOnline_ShouldStoreSessionId() {
        Long creatorId = 1L;
        String sessionId = "session-123";

        registry.markOnline(creatorId, sessionId);

        assertTrue(registry.isOnline(creatorId));
        assertEquals(sessionId, registry.getSessionId(creatorId));
    }

    @Test
    void markOffline_ShouldRemoveSessionId() {
        Long creatorId = 1L;
        String sessionId = "session-123";
        registry.markOnline(creatorId, sessionId);

        registry.markOffline(creatorId);

        assertFalse(registry.isOnline(creatorId));
        assertNull(registry.getSessionId(creatorId));
    }

    @Test
    void getOnlineCreatorIds_ShouldReturnAllIds() {
        registry.markOnline(1L, "s1");
        registry.markOnline(2L, "s2");

        Set<Long> ids = registry.getOnlineCreatorIds();

        assertEquals(2, ids.size());
        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(2L));
    }

    @Test
    void markOnline_NullParameters_ShouldDoNothing() {
        registry.markOnline(null, "session");
        registry.markOnline(1L, null);

        assertEquals(0, registry.getOnlineCreatorIds().size());
    }
}








