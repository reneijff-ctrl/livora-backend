
import os

content = """package com.joinlivora.backend.websocket;

import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.presence.service.*;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.streaming.StreamRoom;
import com.joinlivora.backend.streaming.StreamService;
import com.joinlivora.backend.livestream.service.LiveStreamService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.auth.event.UserLogoutEvent;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.domain.ChatRoomStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
            OnlineCreatorRegistry onlineCreatorRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.presenceTracking = presenceTracking;
        this.viewerCountService = viewerCountService;
        this.eventOrchestrator = eventOrchestrator;
        this.userService = userService;
        this.streamService = streamService;
        this.liveStreamService = liveStreamService;
        this.creatorProfileService = creatorProfileService;
        this.onlineCreatorRegistry = onlineCreatorRegistry;
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
            sessionRegistry.addSubscription(sessionId, subscriptionId, destination);
            String principalId = sessionRegistry.getPrincipal(sessionId);
            String ip = sessionRegistry.getIp(sessionId);
            String userAgent = sessionRegistry.getUserAgent(sessionId);
            handleJoinStream(destination, principalId, sessionId, ip, userAgent);
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
            } catch (Exception e) {
                log.error("Error handling stream join: {}", e.getMessage());
            }
        }

        if (creatorUserId != null) {
            User user = (principalId != null && !"anonymous".equals(principalId)) ? userService.resolveUserFromSubject(principalId).orElse(null) : null;
            viewerCountService.incrementViewerCount(creatorUserId, user != null ? user.getId() : null, sessionId, ip, userAgent);
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
            User user = (principalId != null && !"anonymous".equals(principalId)) ? userService.resolveUserFromSubject(principalId).orElse(null) : null;
            viewerCountService.decrementViewerCount(creatorUserId, user != null ? user.getId() : null, sessionId, ip, userAgent);
        }
    }

    public void broadcastPresence() {
        eventOrchestrator.broadcastPresence(presenceTracking.getOnlineUsersCount());
    }

    public String getIpForSession(String sessionId) { return sessionRegistry.getIp(sessionId); }
    public String getUserAgentForSession(String sessionId) { return sessionRegistry.getUserAgent(sessionId); }
    public long getOnlineUsersCount() { return presenceTracking.getOnlineUsersCount(); }
    public long getActiveSessionsCount() { return sessionRegistry.getActiveSessionsCount(); }
    public boolean isOnline(Long userId) { return presenceTracking.isUserOnline(userId); }

    @Scheduled(fixedRate = 30000)
    public void refreshPresence() {
        Set<Long> userIds = sessionRegistry.getAllActiveSessions().keySet().stream()
                .map(sessionRegistry::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        presenceTracking.refreshPresence(userIds);
    }
}
\"\"\"

with open('src/main/java/com/joinlivora/backend/websocket/PresenceService.java', 'w', encoding='utf-8') as f:
    f.write(content)
"""

with open('refactor_presence.py', 'w', encoding='utf-8') as f:
    f.write(content)
