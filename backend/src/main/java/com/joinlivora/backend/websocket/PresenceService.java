package com.joinlivora.backend.websocket;

import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.presence.service.*;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.creator.service.OnlineStatusService;
import com.joinlivora.backend.streaming.StreamRoom;
import com.joinlivora.backend.streaming.StreamService;
import com.joinlivora.backend.livestream.service.LiveStreamService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import com.joinlivora.backend.streaming.CreatorGoLiveService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.auth.event.UserLogoutEvent;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.domain.ChatRoomStatus;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import com.joinlivora.backend.creator.follow.repository.CreatorFollowRepository;
import com.joinlivora.backend.streaming.service.StreamModeratorService;
import com.joinlivora.backend.user.dto.PublicViewerResponse;
import com.joinlivora.backend.user.dto.UserResponse;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class PresenceService {

    private final SessionRegistryService sessionRegistry;
    private final PresenceTrackingService presenceTracking;
    private final ViewerCountService viewerCountService;
    private final PresenceEventOrchestrator eventOrchestrator;
    private final UserService userService;
    private final StreamService streamService;
    private final LiveStreamService liveStreamService;
    private final CreatorProfileService creatorProfileService;
    private final OnlineCreatorRegistry onlineCreatorRegistry;
    private final LiveViewerCounterService liveViewerCounterService;
    private final CreatorFollowRepository creatorFollowRepository;
    private final StreamModeratorService streamModeratorService;

    @Autowired
    public PresenceService(
            SessionRegistryService sessionRegistry,
            PresenceTrackingService presenceTracking,
            ViewerCountService viewerCountService,
            PresenceEventOrchestrator eventOrchestrator,
            @Lazy UserService userService,
            StreamService streamService,
            LiveStreamService liveStreamService,
            @Lazy CreatorProfileService creatorProfileService,
            OnlineCreatorRegistry onlineCreatorRegistry,
            LiveViewerCounterService liveViewerCounterService,
            CreatorFollowRepository creatorFollowRepository,
            @Lazy StreamModeratorService streamModeratorService) {
        this.sessionRegistry = sessionRegistry;
        this.presenceTracking = presenceTracking;
        this.viewerCountService = viewerCountService;
        this.eventOrchestrator = eventOrchestrator;
        this.userService = userService;
        this.streamService = streamService;
        this.liveStreamService = liveStreamService;
        this.creatorProfileService = creatorProfileService;
        this.onlineCreatorRegistry = onlineCreatorRegistry;
        this.liveViewerCounterService = liveViewerCounterService;
        this.creatorFollowRepository = creatorFollowRepository;
        this.streamModeratorService = streamModeratorService;
    }

    public static com.joinlivora.backend.presence.service.BrokerAvailabilityListener createAlwaysAvailableBrokerListener() {
        com.joinlivora.backend.presence.service.BrokerAvailabilityListener listener = new com.joinlivora.backend.presence.service.BrokerAvailabilityListener();
        listener.setBrokerAvailable(true);
        return listener;
    }


    @EventListener
    @Transactional(readOnly = true)
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        Map<String, Object> attributes = headerAccessor.getSessionAttributes();
        String ip = attributes != null ? (String) attributes.get("ip") : null;
        String userAgent = attributes != null ? (String) attributes.get("userAgent") : null;

        Principal principal = event.getUser();
        String principalName = (principal != null) ? principal.getName() : "anonymous";
        log.info("SessionConnectEvent: principal={}, sessionId={}", principalName, sessionId);

        if (principal != null && sessionId != null) {
            Long userId = null;
            Role role = null;
            User user = null;
            
            try {
                if (principal instanceof org.springframework.security.core.Authentication auth) {
                    if (auth.getPrincipal() instanceof com.joinlivora.backend.websocket.StompPrincipal sp) {
                        userId = Long.valueOf(sp.getUserId());
                        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CREATOR"))) {
                            role = Role.CREATOR;
                        }
                    } else if (auth.getPrincipal() instanceof com.joinlivora.backend.security.UserPrincipal up) {
                        userId = up.getUserId();
                        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CREATOR"))) {
                            role = Role.CREATOR;
                        }
                    }
                }
                
                if (userId == null) {
                    try {
                        userId = Long.valueOf(principalName);
                        user = userService.getById(userId);
                        role = user.getRole();
                    } catch (NumberFormatException e) {
                        user = userService.getByEmail(principalName);
                        userId = user.getId();
                        role = user.getRole();
                    }
                }
                
                // Fetch user once if not already loaded from the fallback path above
                if (user == null) {
                    user = userService.getById(userId);
                }
                
                Long creatorId = null;
                if (role == Role.CREATOR) {
                    if (creatorProfileService != null) {
                        creatorProfileService.initializeCreatorProfile(user);
                        creatorId = creatorProfileService.getCreatorIdByUserId(userId).orElse(null);
                    }
                    
                    log.info("CREATOR ONLINE: {} (userId: {})", creatorId, userId);
                    presenceTracking.markCreatorOnline(creatorId);
                    onlineCreatorRegistry.markOnline(creatorId, sessionId);
                    
                    eventOrchestrator.broadcastCreatorPresence(userId, creatorId, true);
                    
                    if (liveStreamService.isStreamActive(userId)) {
                        eventOrchestrator.activateChatRooms(creatorId);
                    }
                }
                
                sessionRegistry.registerSession(sessionId, principalName, userId, creatorId, ip, userAgent);
                presenceTracking.markUserOnline(userId);
                
                String email = user.getEmail() != null ? user.getEmail() : principalName;
                eventOrchestrator.publishJoinEvent(principalName, email);
                
            } catch (Exception e) {
                log.warn("WebSocket: Could not resolve user info for presence: {}. Error: {}", principal.getName(), e.getMessage());
            }

            broadcastPresence();
        }
    }

    @EventListener
    public void handleLogout(UserLogoutEvent event) {
        String email = event.getEmail();
        log.info("Presence: Handling logout for user {}", email);

        Long userIdResolved;
        try {
            userIdResolved = userService.getByEmail(email).getId();
        } catch (Exception e) {
            log.debug("Presence: Could not resolve userId for logout of {}: {}", email, e.getMessage());
            return;
        }

        String userIdStr = userIdResolved.toString();

        List<String> sessionsToCleanup = sessionRegistry.getAllActiveSessions().entrySet().stream()
                .filter(e -> e.getValue().equals(userIdStr) || e.getValue().equals(email))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (String sessionId : sessionsToCleanup) {
            handleManualDisconnect(sessionId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        handleManualDisconnect(sessionId);
    }

    private void handleManualDisconnect(String sessionId) {
        String principalId = sessionRegistry.getPrincipal(sessionId);
        Long userId = sessionRegistry.getUserId(sessionId);
        Long creatorId = sessionRegistry.getCreatorId(sessionId);
        String ip = sessionRegistry.getIp(sessionId);
        String userAgent = sessionRegistry.getUserAgent(sessionId);

        if (sessionId != null) {
            boolean isLastSession = false;
            if (userId != null) {
                isLastSession = presenceTracking.markUserOffline(userId);
            }
            processDisconnect(sessionId, principalId, userId, creatorId, ip, userAgent, isLastSession);
            sessionRegistry.unregisterSession(sessionId);
        }
    }

    private void processDisconnect(String sessionId, String principalId, Long userId, Long creatorId, String ip, String userAgent, boolean isLastSession) {
        if (isLastSession && creatorId != null) {
            log.info("CREATOR OFFLINE: {} (userId: {})", creatorId, userId);
            presenceTracking.markCreatorOffline(creatorId);
            onlineCreatorRegistry.markOffline(creatorId);
            eventOrchestrator.broadcastCreatorPresence(userId, creatorId, false);

            try {
                if (!liveStreamService.isStreamActive(userId)) {
                    eventOrchestrator.pauseChatRooms(creatorId);
                }
                eventOrchestrator.broadcastCreatorLeft(userId, creatorId);
            } catch (Exception e) {
                log.warn("Could not pause chat rooms for creator {} during disconnect (may be expected): {}", creatorId, e.getMessage());
            }
        }

        Set<String> destinations = sessionRegistry.getSubscriptions(sessionId);
        destinations.forEach(dest -> handleLeaveStream(dest, principalId, sessionId, ip, userAgent));
        
        if (principalId != null) {
            log.info("WebSocket: User {} disconnected (Session: {})", principalId, sessionId);
            broadcastPresence();
            
            String email = principalId;
            try {
                if (userId != null) {
                    email = userService.getById(userId).getEmail();
                }
            } catch (Exception ignored) {}
            eventOrchestrator.publishLeaveEvent(principalId, email);

            if (isLastSession && userId != null) {
                streamService.findByCreatorId(userId).ifPresent(room -> {
                    if (room.isLive()) {
                        eventOrchestrator.broadcastSystemMessage(room.getCreator().getId(), "Creator went offline (WebSocket disconnected)");
                    }
                });
            }
        }
    }

    @EventListener
    public void handleSubscriptionEvent(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();
        String subscriptionId = headerAccessor.getSubscriptionId();

        if (sessionId != null && destination != null && subscriptionId != null) {
            if (sessionRegistry.addSubscription(sessionId, subscriptionId, destination)) {
                String principalId = sessionRegistry.getPrincipal(sessionId);
                String ip = sessionRegistry.getIp(sessionId);
                String userAgent = sessionRegistry.getUserAgent(sessionId);
                handleJoinStream(destination, principalId, sessionId, ip, userAgent);
            }
        }
    }

    @EventListener
    public void handleUnsubscribeEvent(SessionUnsubscribeEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String subscriptionId = headerAccessor.getSubscriptionId();

        if (sessionId != null && subscriptionId != null) {
            String destination = sessionRegistry.removeSubscription(sessionId, subscriptionId);
            if (destination != null) {
                String principalId = sessionRegistry.getPrincipal(sessionId);
                String ip = sessionRegistry.getIp(sessionId);
                String userAgent = sessionRegistry.getUserAgent(sessionId);
                handleLeaveStream(destination, principalId, sessionId, ip, userAgent);
            }
        }
    }

    private void handleJoinStream(String destination, String principalId, String sessionId, String ip, String userAgent) {
        Long creatorUserId = null;
        UUID streamId = null;

        try {
            if (destination.startsWith("/exchange/amq.topic/viewers.")) {
                String idStr = destination.substring("/exchange/amq.topic/viewers.".length());
                try {
                    streamId = UUID.fromString(idStr);
                } catch (Exception e) {
                    try { creatorUserId = Long.parseLong(idStr); } catch (Exception ignored) {}
                }
            } else if (destination.startsWith("/exchange/amq.topic/stream.") && destination.endsWith(".video")) {
                String idPart = destination.substring("/exchange/amq.topic/stream.".length(), destination.length() - ".video".length());
                if (idPart.endsWith(".")) idPart = idPart.substring(0, idPart.length() - 1);
                streamId = UUID.fromString(idPart);
            }
        } catch (Exception ignored) {}

        if (streamId != null) {
            try {
                StreamRoom room = streamService.getRoom(streamId);
                creatorUserId = room.getCreator().getId();
                sessionRegistry.trackJoinTime(sessionId, destination, Instant.now());

                User user = (principalId != null && !"anonymous".equals(principalId)) ? userService.resolveUserFromSubject(principalId).orElse(null) : null;
                eventOrchestrator.publishAnalyticsEvent(AnalyticsEventType.STREAM_JOIN, user, Map.of("roomId", streamId, "creator", creatorUserId));
                
                String botUserName = user != null ? (user.getDisplayName() != null ? user.getDisplayName() : user.getEmail().split("@")[0]) : "anonymous";
                boolean isViewerOnly = user == null || (user.getRole() != Role.CREATOR && user.getRole() != Role.ADMIN);
                if (isViewerOnly) {
                    eventOrchestrator.notifyStreamJoin(creatorUserId, botUserName, principalId);
                }

                // Reintroduce "new account join clustering"
                if (user != null && user.getCreatedAt() != null) {
                    Instant now = Instant.now();
                    if (user.getCreatedAt().isAfter(now.minus(Duration.ofHours(24)))) {
                        presenceTracking.trackNewAccountJoin(creatorUserId, user.getId(), streamId);
                    }
                }
            } catch (Exception e) {
                log.debug("Error handling stream join (stream may have ended): {}", e.getMessage());
            }
        }

        if (creatorUserId != null) {
            // When creatorUserId was resolved from a Long (not UUID), streamId is null
            // and notifyStreamJoin was not called above â€” call it here for chat history replay
            if (streamId == null) {
                try {
                    User user = (principalId != null && !"anonymous".equals(principalId)) ? userService.resolveUserFromSubject(principalId).orElse(null) : null;
                    String botUserName = user != null ? (user.getDisplayName() != null ? user.getDisplayName() : user.getEmail().split("@")[0]) : "viewer";
                    boolean isViewerOnly = user == null || (user.getRole() != Role.CREATOR && user.getRole() != Role.ADMIN);
                    if (isViewerOnly) {
                        eventOrchestrator.notifyStreamJoin(creatorUserId, botUserName, principalId);
                    }
                } catch (Exception e) {
                    log.debug("Error notifying stream join for creatorUserId {} (stream may have ended): {}", creatorUserId, e.getMessage());
                }
            }

            UUID activeStreamId = liveViewerCounterService != null ? liveViewerCounterService.getActiveStreamUuid(creatorUserId) : null;
            if (activeStreamId != null) {
                User user = (principalId != null && !"anonymous".equals(principalId)) ? userService.resolveUserFromSubject(principalId).orElse(null) : null;
                viewerCountService.incrementViewerCount(activeStreamId, creatorUserId, user != null ? user.getId() : null, sessionId, ip, userAgent);
            }
        }
    }

    private void handleLeaveStream(String destination, String principalId, String sessionId, String ip, String userAgent) {
        Long creatorUserId = null;
        UUID streamId = null;

        try {
            if (destination.startsWith("/exchange/amq.topic/viewers.")) {
                String idStr = destination.substring("/exchange/amq.topic/viewers.".length());
                try { streamId = UUID.fromString(idStr); } catch (Exception e) {
                    try { creatorUserId = Long.parseLong(idStr); } catch (Exception ignored) {}
                }
            } else if (destination.startsWith("/exchange/amq.topic/stream.") && destination.endsWith(".video")) {
                String idPart = destination.substring("/exchange/amq.topic/stream.".length(), destination.length() - ".video".length());
                if (idPart.endsWith(".")) idPart = idPart.substring(0, idPart.length() - 1);
                streamId = UUID.fromString(idPart);
            } else if (destination.startsWith("/exchange/amq.topic/chat.")) {
                String idStr = destination.substring("/exchange/amq.topic/chat.".length());
                try { creatorUserId = Long.parseLong(idStr); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        if (streamId != null) {
            try {
                StreamRoom room = streamService.getRoom(streamId);
                creatorUserId = room.getCreator().getId();
                Instant joinTime = sessionRegistry.removeJoinTime(sessionId, destination);
                long durationSeconds = joinTime != null ? Duration.between(joinTime, Instant.now()).getSeconds() : 0;

                User user = (principalId != null && !"anonymous".equals(principalId)) ? userService.resolveUserFromSubject(principalId).orElse(null) : null;
                eventOrchestrator.publishAnalyticsEvent(AnalyticsEventType.STREAM_LEAVE, user, Map.of("roomId", streamId, "creator", creatorUserId, "duration", durationSeconds));
            } catch (Exception e) {
                log.debug("Error handling stream leave (stream may have ended): {}", e.getMessage());
            }
        }

        if (creatorUserId != null) {
            UUID activeStreamId = liveViewerCounterService != null ? liveViewerCounterService.getActiveStreamUuid(creatorUserId) : null;
            if (activeStreamId != null) {
                sessionRegistry.markStreamLeft(sessionId, null);
                User user = (principalId != null && !"anonymous".equals(principalId)) ? userService.resolveUserFromSubject(principalId).orElse(null) : null;
                viewerCountService.decrementViewerCount(activeStreamId, creatorUserId, user != null ? user.getId() : null, sessionId, ip, userAgent);
            }
        }
    }

    public void broadcastPresence() {
        eventOrchestrator.broadcastPresence(presenceTracking.getOnlineUsersCount());
    }

    public String getIpForSession(String sessionId) { return sessionRegistry.getIp(sessionId); }
    public String getUserAgentForSession(String sessionId) { return sessionRegistry.getUserAgent(sessionId); }
    
    public String getIpForCreator(Long creatorId) {
        String sessionId = onlineCreatorRegistry.getSessionId(creatorId);
        return sessionRegistry.getIp(sessionId);
    }

    public String getUserAgentForCreator(Long creatorId) {
        String sessionId = onlineCreatorRegistry.getSessionId(creatorId);
        return sessionRegistry.getUserAgent(sessionId);
    }

    public long getOnlineUsersCount() { return presenceTracking.getOnlineUsersCount(); }
    public long getActiveSessionsCount() { return sessionRegistry.getActiveSessionsCount(); }
    public boolean isOnline(Long userId) { return presenceTracking.isUserOnline(userId); }

    public boolean isSubscribedTo(String sessionId, String destination) {
        return sessionRegistry.isSubscribedTo(sessionId, destination);
    }

    public long getRecentNewAccountJoinCount(Long creatorUserId, Duration duration) {
        return presenceTracking.getRecentNewAccountJoinCount(creatorUserId, duration);
    }

    public List<UserResponse> getUserInfoBySessionIds(Set<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) return java.util.Collections.emptyList();
        
        Set<Long> uniqueUserIds = new java.util.HashSet<>();
        Map<Long, String> userPrincipals = new java.util.HashMap<>();
        
        for (String sessionId : sessionIds) {
            Long userId = sessionRegistry.getUserId(sessionId);
            if (userId != null && uniqueUserIds.add(userId)) {
                userPrincipals.put(userId, sessionRegistry.getPrincipal(sessionId));
            }
        }
        
        if (uniqueUserIds.isEmpty()) return java.util.Collections.emptyList();
        
        List<User> users = userService.findAllByIds(uniqueUserIds);
        return users.stream()
                .map(u -> new UserResponse(
                        u.getId(),
                        userPrincipals.getOrDefault(u.getId(), u.getEmail()),
                        u.getUsername(),
                        u.getDisplayName(),
                        Role.USER))
                .collect(Collectors.toList());
    }

    /**
     * Returns viewer list for a creator using the hybrid merge approach (same as getPublicViewerList)
     * but returning UserResponse objects for the creator dashboard.
     * Merges user IDs from Redis userViewers hash AND viewers SET + sessionRegistry reverse lookup
     * to ensure no connected viewers are dropped.
     */
    public List<UserResponse> getCreatorViewerList(Long creatorUserId) {
        Set<Long> uniqueUserIds = new java.util.HashSet<>();

        // Source 1: userViewers hash (authoritative userId -> sessionId mapping written by Lua)
        Set<Long> hashUserIds = liveViewerCounterService.getAuthenticatedViewerUserIds(creatorUserId);
        uniqueUserIds.addAll(hashUserIds);

        // Source 2: viewers SET -> sessionRegistry reverse lookup (fallback)
        Set<String> sessionIds = liveViewerCounterService.getViewers(creatorUserId);
        for (String sid : sessionIds) {
            Long uid = sessionRegistry.getUserId(sid);
            if (uid != null) {
                uniqueUserIds.add(uid);
            }
        }

        log.debug("VIEWER_LIST_DEBUG: getCreatorViewerList creatorUserId={}, hashUserIds={}, setSessionIds={}, mergedUserIds={}",
                creatorUserId, hashUserIds, sessionIds, uniqueUserIds);

        if (uniqueUserIds.isEmpty()) return java.util.Collections.emptyList();

        List<User> users = userService.findAllByIds(uniqueUserIds);
        return users.stream()
                .filter(u -> u.getRole() == null || u.getRole() != Role.ADMIN)
                .map(u -> new UserResponse(
                        u.getId(),
                        u.getEmail(),
                        u.getUsername(),
                        u.getDisplayName(),
                        Role.USER))
                .collect(Collectors.toList());
    }

    public List<PublicViewerResponse> getPublicViewerList(Long creatorUserId) {
        // Strategy: merge user IDs from two sources for maximum reliability:
        // 1. userViewers hash (authoritative userId -> sessionId mapping written by Lua)
        // 2. viewers SET + sessionRegistry reverse lookup (fallback for any hash read issues)
        Set<Long> uniqueUserIds = new java.util.HashSet<>();

        // Source 1: userViewers hash (direct userId keys)
        Set<Long> hashUserIds = liveViewerCounterService.getAuthenticatedViewerUserIds(creatorUserId);
        uniqueUserIds.addAll(hashUserIds);

        // Source 2: viewers SET -> sessionRegistry reverse lookup (original approach)
        Set<String> sessionIds = liveViewerCounterService.getViewers(creatorUserId);
        for (String sid : sessionIds) {
            Long uid = sessionRegistry.getUserId(sid);
            if (uid != null) {
                uniqueUserIds.add(uid);
            }
        }

        log.debug("VIEWER_LIST_DEBUG: creatorUserId={}, hashUserIds={}, setSessionIds={}, mergedUserIds={}",
                creatorUserId, hashUserIds, sessionIds, uniqueUserIds);

        if (uniqueUserIds.isEmpty()) return java.util.Collections.emptyList();

        List<User> users = userService.findAllByIds(uniqueUserIds);

        log.debug("VIEWER_LIST_DEBUG: loadedUsers={}", users.stream().map(u -> u.getId() + ":" + u.getUsername()).collect(Collectors.joining(",")));

        Set<Long> followerIds = (creatorFollowRepository != null && !uniqueUserIds.isEmpty())
                ? creatorFollowRepository.findFollowerIdsByCreatorIdAndFollowerIds(creatorUserId, uniqueUserIds)
                : java.util.Collections.emptySet();

        Set<Long> moderatorIds = (streamModeratorService != null)
                ? streamModeratorService.getModeratorIds(creatorUserId)
                : java.util.Collections.emptySet();

        List<PublicViewerResponse> result = users.stream()
                .filter(u -> u.getRole() == null || u.getRole() != Role.ADMIN)
                .map(u -> new PublicViewerResponse(u.getId(), u.getUsername(), u.getDisplayName(), followerIds.contains(u.getId()), moderatorIds.contains(u.getId())))
                .collect(Collectors.toList());

        log.debug("VIEWER_LIST_DEBUG: returning {} viewer DTOs", result.size());
        return result;
    }

    @Scheduled(fixedRate = 30000)
    public void refreshPresence() {
        Set<Long> userIds = sessionRegistry.getAllActiveSessions().keySet().stream()
                .map(sessionRegistry::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        presenceTracking.refreshPresence(userIds);
    }
}
