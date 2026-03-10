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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

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
            LiveViewerCounterService liveViewerCounterService) {
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
    }

    /**
     * Legacy constructor for backward compatibility with tests and old wiring.
     * Maps old dependencies to new service architecture.
     */
    public PresenceService(
            @Lazy SimpMessagingTemplate messagingTemplate,
            StreamService streamService,
            @Lazy AnalyticsEventPublisher analyticsEventPublisher,
            @Lazy UserService userService,
            OnlineStatusService onlineStatusService,
            CreatorPresenceService creatorPresenceService,
            OnlineCreatorRegistry onlineCreatorRegistry,
            CreatorRepository creatorRepository,
            ChatRoomRepository chatRoomRepositoryV2,
            ChatRoomService chatRoomServiceV2,
            CreatorGoLiveService creatorGoLiveService,
            LiveViewerCounterService liveViewerCounterService,
            StreamAssistantBotService streamAssistantBotService,
            RedisTemplate<String, Object> redisTemplate,
            LiveStreamService liveStreamService) {
        this(
            new SessionRegistryService(),
            new PresenceTrackingService(redisTemplate, onlineStatusService),
            new ViewerCountService(liveViewerCounterService),
            new PresenceEventOrchestrator(messagingTemplate, analyticsEventPublisher, streamAssistantBotService, chatRoomServiceV2, createAlwaysAvailableBrokerListener()),
            userService,
            streamService,
            liveStreamService,
            null, // Will be lazily resolved in production or remains null in tests if not needed
            onlineCreatorRegistry,
            liveViewerCounterService
        );
    }

    public static com.joinlivora.backend.presence.service.BrokerAvailabilityListener createAlwaysAvailableBrokerListener() {
        com.joinlivora.backend.presence.service.BrokerAvailabilityListener listener = new com.joinlivora.backend.presence.service.BrokerAvailabilityListener();
        listener.setBrokerAvailable(true);
        return listener;
    }

    /**
     * Legacy constructor variation for tests.
     */
    public PresenceService(
            @Lazy SimpMessagingTemplate messagingTemplate,
            StreamService streamService,
            @Lazy AnalyticsEventPublisher analyticsEventPublisher,
            @Lazy UserService userService,
            OnlineStatusService onlineStatusService,
            CreatorPresenceService creatorPresenceService,
            OnlineCreatorRegistry onlineCreatorRegistry,
            CreatorRepository creatorRepository,
            ChatRoomRepository chatRoomRepositoryV2,
            ChatRoomService chatRoomServiceV2) {
        this(messagingTemplate, streamService, analyticsEventPublisher, userService, onlineStatusService,
                creatorPresenceService, onlineCreatorRegistry, creatorRepository, chatRoomRepositoryV2,
                chatRoomServiceV2, null, null, null, null, null);
    }


    @EventListener
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
                        User user = userService.getById(userId);
                        role = user.getRole();
                    } catch (NumberFormatException e) {
                        User user = userService.getByEmail(principalName);
                        userId = user.getId();
                        role = user.getRole();
                    }
                }
                
                Long creatorId = null;
                if (role == Role.CREATOR) {
                    User user = userService.getById(userId);
                    // Business Logic Initialization moved to CreatorProfileService
                    creatorProfileService.initializeCreatorProfile(user);
                    creatorId = creatorProfileService.getCreatorIdByUserId(userId).orElse(null);
                    
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
                
                String email = principalName;
                try {
                    email = userService.getById(userId).getEmail();
                } catch (Exception ignored) {}
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
                log.error("Error pausing chat rooms for creator {}: {}", creatorId, e.getMessage());
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
            if (destination.startsWith("/topic/viewers/")) {
                String idStr = destination.substring("/topic/viewers/".length());
                try {
                    streamId = UUID.fromString(idStr);
                } catch (Exception e) {
                    try { creatorUserId = Long.parseLong(idStr); } catch (Exception ignored) {}
                }
            } else if (destination.startsWith("/topic/stream/") && destination.endsWith("/video")) {
                String idPart = destination.substring("/topic/stream/".length(), destination.length() - "/video".length());
                if (idPart.endsWith("/")) idPart = idPart.substring(0, idPart.length() - 1);
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
                eventOrchestrator.notifyStreamJoin(creatorUserId, botUserName);

                // Reintroduce "new account join clustering"
                if (user != null && user.getCreatedAt() != null) {
                    Instant now = Instant.now();
                    if (user.getCreatedAt().isAfter(now.minus(Duration.ofHours(24)))) {
                        presenceTracking.trackNewAccountJoin(creatorUserId, user.getId(), streamId);
                    }
                }
            } catch (Exception e) {
                log.error("Error handling stream join: {}", e.getMessage());
            }
        }

        if (creatorUserId != null) {
            Long streamSessionId = liveViewerCounterService != null ? liveViewerCounterService.getActiveSessionId(creatorUserId) : null;
            if (streamSessionId != null && sessionRegistry.markStreamJoined(sessionId, streamSessionId)) {
                User user = (principalId != null && !"anonymous".equals(principalId)) ? userService.resolveUserFromSubject(principalId).orElse(null) : null;
                viewerCountService.incrementViewerCount(streamSessionId, creatorUserId, user != null ? user.getId() : null, sessionId, ip, userAgent);
            }
        }
    }

    private void handleLeaveStream(String destination, String principalId, String sessionId, String ip, String userAgent) {
        Long creatorUserId = null;
        UUID streamId = null;

        try {
            if (destination.startsWith("/topic/viewers/")) {
                String idStr = destination.substring("/topic/viewers/".length());
                try { streamId = UUID.fromString(idStr); } catch (Exception e) {
                    try { creatorUserId = Long.parseLong(idStr); } catch (Exception ignored) {}
                }
            } else if (destination.startsWith("/topic/stream/") && destination.endsWith("/video")) {
                String idPart = destination.substring("/topic/stream/".length(), destination.length() - "/video".length());
                if (idPart.endsWith("/")) idPart = idPart.substring(0, idPart.length() - 1);
                streamId = UUID.fromString(idPart);
            } else if (destination.startsWith("/topic/chat/")) {
                String idStr = destination.substring("/topic/chat/".length());
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
                log.error("Error handling stream leave: {}", e.getMessage());
            }
        }

        if (creatorUserId != null) {
            Long streamSessionId = liveViewerCounterService != null ? liveViewerCounterService.getActiveSessionId(creatorUserId) : null;
            if (streamSessionId != null && sessionRegistry.markStreamLeft(sessionId, streamSessionId)) {
                User user = (principalId != null && !"anonymous".equals(principalId)) ? userService.resolveUserFromSubject(principalId).orElse(null) : null;
                viewerCountService.decrementViewerCount(streamSessionId, creatorUserId, user != null ? user.getId() : null, sessionId, ip, userAgent);
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
        
        Map<Long, UserResponse> uniqueUsers = new java.util.HashMap<>();
        
        for (String sessionId : sessionIds) {
            Long userId = sessionRegistry.getUserId(sessionId);
            if (userId != null) {
                if (!uniqueUsers.containsKey(userId)) {
                    String principalId = sessionRegistry.getPrincipal(sessionId);
                    uniqueUsers.put(userId, new UserResponse(userId, principalId, Role.USER));
                }
            }
        }
        
        return new java.util.ArrayList<>(uniqueUsers.values());
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
