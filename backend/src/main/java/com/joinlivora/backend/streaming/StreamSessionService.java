package com.joinlivora.backend.streaming;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StreamSessionService {

    private final Map<Long, StreamSession> activeSessions = new ConcurrentHashMap<>();

    public boolean isViewerAlreadyConnected(Long sessionId, Long viewerId) {
        return activeSessions.containsKey(sessionId)
            && activeSessions.get(sessionId).getViewers().contains(viewerId);
    }

    public static class StreamSession {
        private final Set<Long> viewers = ConcurrentHashMap.newKeySet();

        public Set<Long> getViewers() {
            return viewers;
        }
    }
}
