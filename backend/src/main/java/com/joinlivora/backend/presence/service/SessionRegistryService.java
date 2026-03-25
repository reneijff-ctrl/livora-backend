package com.joinlivora.backend.presence.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing WebSocket sessions and their associated metadata.
 * Responsibilities:
 * - register session
 * - unregister session
 * - map sessionId to userId, creatorId, and streamId.
 */
@Service
@Slf4j
public class SessionRegistryService {

    // Map of session ID to user ID string (principal name)
    private final Map<String, String> sessionToPrincipal = new ConcurrentHashMap<>();

    // Map of session ID to user ID (if authenticated)
    private final Map<String, Long> sessionToUserId = new ConcurrentHashMap<>();

    // Map of session ID to creator entity ID (Creator.id)
    private final Map<String, Long> sessionToCreatorId = new ConcurrentHashMap<>();

    // Map of session ID to client IP address
    private final Map<String, String> sessionToIp = new ConcurrentHashMap<>();

    // Map of session ID to client user agent
    private final Map<String, String> sessionToUserAgent = new ConcurrentHashMap<>();

    // Map of session ID to Set of destinations (subscriptions)
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    // Map of session ID to (Subscription ID -> Destination)
    private final Map<String, Map<String, String>> subscriptionToDestination = new ConcurrentHashMap<>();

    // Map of session ID -> Destination -> Join Instant
    private final Map<String, Map<String, Instant>> sessionJoinTimes = new ConcurrentHashMap<>();

    // Map of session ID -> Set of streamSessionIds (to ensure idempotency across multiple topics)
    private final Map<String, Set<Long>> sessionJoinedStreams = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, String principalName, Long userId, Long creatorId, String ip, String userAgent) {
        if (principalName != null) sessionToPrincipal.put(sessionId, principalName);
        if (userId != null) sessionToUserId.put(sessionId, userId);
        if (creatorId != null) sessionToCreatorId.put(sessionId, creatorId);
        if (ip != null) sessionToIp.put(sessionId, ip);
        if (userAgent != null) sessionToUserAgent.put(sessionId, userAgent);
        log.debug("Session registered: {}", sessionId);
    }

    public void unregisterSession(String sessionId) {
        sessionToPrincipal.remove(sessionId);
        sessionToUserId.remove(sessionId);
        sessionToCreatorId.remove(sessionId);
        sessionToIp.remove(sessionId);
        sessionToUserAgent.remove(sessionId);
        sessionSubscriptions.remove(sessionId);
        subscriptionToDestination.remove(sessionId);
        sessionJoinTimes.remove(sessionId);
        sessionJoinedStreams.remove(sessionId);
        log.debug("Session unregistered: {}", sessionId);
    }

    public String getPrincipal(String sessionId) {
        return sessionToPrincipal.get(sessionId);
    }

    public Long getUserId(String sessionId) {
        return sessionToUserId.get(sessionId);
    }

    public Long getCreatorId(String sessionId) {
        return sessionToCreatorId.get(sessionId);
    }

    public String getIp(String sessionId) {
        return sessionToIp.get(sessionId);
    }

    public String getUserAgent(String sessionId) {
        return sessionToUserAgent.get(sessionId);
    }

    public boolean addSubscription(String sessionId, String subscriptionId, String destination) {
        boolean isNewDestination = sessionSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(destination);
        subscriptionToDestination.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(subscriptionId, destination);
        return isNewDestination;
    }

    public String removeSubscription(String sessionId, String subscriptionId) {
        Map<String, String> subs = subscriptionToDestination.get(sessionId);
        if (subs != null) {
            String destination = subs.remove(subscriptionId);
            if (destination != null) {
                Set<String> destinations = sessionSubscriptions.get(sessionId);
                if (destinations != null) {
                    destinations.remove(destination);
                }
                return destination;
            }
        }
        return null;
    }

    public boolean isSubscribedTo(String sessionId, String destination) {
        Set<String> subs = sessionSubscriptions.get(sessionId);
        return subs != null && subs.contains(destination);
    }

    public Set<String> getSubscriptions(String sessionId) {
        return sessionSubscriptions.getOrDefault(sessionId, java.util.Collections.emptySet());
    }

    public void trackJoinTime(String sessionId, String destination, Instant joinTime) {
        sessionJoinTimes.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(destination, joinTime);
    }

    public Instant getJoinTime(String sessionId, String destination) {
        Map<String, Instant> joins = sessionJoinTimes.get(sessionId);
        return joins != null ? joins.get(destination) : null;
    }

    public Instant removeJoinTime(String sessionId, String destination) {
        Map<String, Instant> joins = sessionJoinTimes.get(sessionId);
        return joins != null ? joins.remove(destination) : null;
    }

    public boolean markStreamJoined(String sessionId, Long streamSessionId) {
        if (streamSessionId == null) return false;
        return sessionJoinedStreams.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(streamSessionId);
    }

    public boolean markStreamLeft(String sessionId, Long streamSessionId) {
        if (streamSessionId == null) return false;
        Set<Long> streams = sessionJoinedStreams.get(sessionId);
        return streams != null && streams.remove(streamSessionId);
    }

    public Set<Long> getJoinedStreams(String sessionId) {
        return sessionJoinedStreams.getOrDefault(sessionId, java.util.Collections.emptySet());
    }

    public Map<String, String> getAllActiveSessions() {
        return java.util.Collections.unmodifiableMap(sessionToPrincipal);
    }

    public long getActiveSessionsCount() {
        return sessionToPrincipal.size();
    }
}
