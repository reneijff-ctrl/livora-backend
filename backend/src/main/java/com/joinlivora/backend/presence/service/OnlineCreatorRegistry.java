package com.joinlivora.backend.presence.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry for online creators.
 * Maintains a mapping of creator to their WebSocket sessionId.
 */
@Service
public class OnlineCreatorRegistry {
    
    // Map of creator to sessionId
    private final Map<Long, String> registry = new ConcurrentHashMap<>();

    /**
     * Mark a creator as online with their session ID.
     */
    public void markOnline(Long creatorId, String sessionId) {
        if (creatorId != null && sessionId != null) {
            registry.put(creatorId, sessionId);
        }
    }

    /**
     * Mark a creator as offline.
     */
    public void markOffline(Long creatorId) {
        if (creatorId != null) {
            registry.remove(creatorId);
        }
    }

    /**
     * Check if a creator is currently online.
     */
    public boolean isOnline(Long creatorId) {
        return creatorId != null && registry.containsKey(creatorId);
    }

    /**
     * Get the sessionId for an online creator.
     */
    public String getSessionId(Long creatorId) {
        return creatorId != null ? registry.get(creatorId) : null;
    }

    /**
     * Get all online creator IDs.
     */
    public java.util.Set<Long> getOnlineCreatorIds() {
        return registry.keySet();
    }
}
