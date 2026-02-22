package com.joinlivora.backend.websocket;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.presence.service.OnlineCreatorRegistry;
import com.joinlivora.backend.creator.service.OnlineStatusService;
import com.joinlivora.backend.streaming.StreamRoom;
import com.joinlivora.backend.streaming.StreamService;
import com.joinlivora.backend.streaming.CreatorGoLiveService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
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

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class PresenceService {

    private final SimpMessagingTemplate messagingTemplate;
    private final StreamService streamService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final UserService userService;
    private final OnlineStatusService onlineStatusService;
    private final CreatorPresenceService creatorPresenceService;
    private final OnlineCreatorRegistry onlineCreatorRegistry;
    private final CreatorRepository creatorRepository;
    private final ChatRoomRepository chatRoomRepositoryV2;
    private final ChatRoomService chatRoomServiceV2;
    private final CreatorGoLiveService creatorGoLiveService;
    private final LiveViewerCounterService liveViewerCounterService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean redisEnabled;

    private static final String ONLINE_USERS_KEY = "online_users";
    private static final String USER_SESSION_COUNT_PREFIX = "user_session_count:";

    @Autowired
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
            RedisTemplate<String, Object> redisTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.streamService = streamService;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.userService = userService;
        this.onlineStatusService = onlineStatusService;
        this.creatorPresenceService = creatorPresenceService;
        this.onlineCreatorRegistry = onlineCreatorRegistry;
        this.creatorRepository = creatorRepository;
        this.chatRoomRepositoryV2 = chatRoomRepositoryV2;
        this.chatRoomServiceV2 = chatRoomServiceV2;
        this.creatorGoLiveService = creatorGoLiveService;
        this.liveViewerCounterService = liveViewerCounterService;
        this.redisTemplate = redisTemplate;
        this.redisEnabled = (onlineStatusService != null) && onlineStatusService.isAvailable();
        if (!redisEnabled) {
            log.info("Redis-backed creator status disabled (OnlineStatusService unavailable)");
        }
    }

    // Constructor without RedisTemplate (e.g., dev profile/tests)
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
            LiveViewerCounterService liveViewerCounterService) {
        this.messagingTemplate = messagingTemplate;
        this.streamService = streamService;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.userService = userService;
        this.onlineStatusService = onlineStatusService;
        this.creatorPresenceService = creatorPresenceService;
        this.onlineCreatorRegistry = onlineCreatorRegistry;
        this.creatorRepository = creatorRepository;
        this.chatRoomRepositoryV2 = chatRoomRepositoryV2;
        this.chatRoomServiceV2 = chatRoomServiceV2;
        this.creatorGoLiveService = creatorGoLiveService;
        this.liveViewerCounterService = liveViewerCounterService;
        this.redisTemplate = null;
        this.redisEnabled = (onlineStatusService != null) && onlineStatusService.isAvailable();
        if (!redisEnabled) {
            log.info("Redis-backed creator status disabled (OnlineStatusService unavailable)");
        }
    }

    // Backward-compatible constructor for tests and legacy wiring
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
            LiveViewerCounterService liveViewerCounterService) {
        this(messagingTemplate, streamService, analyticsEventPublisher, userService, onlineStatusService,
                creatorPresenceService, onlineCreatorRegistry, creatorRepository, chatRoomRepositoryV2,
                chatRoomServiceV2, null, liveViewerCounterService);
    }

    // Legacy constructor without LiveViewerCounterService (kept for test compatibility)
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
                chatRoomServiceV2, null, null);
    }

    // Legacy constructor with CreatorGoLiveService but without LiveViewerCounterService/RedisTemplate
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
            CreatorGoLiveService creatorGoLiveService) {
        this(messagingTemplate, streamService, analyticsEventPublisher, userService, onlineStatusService,
                creatorPresenceService, onlineCreatorRegistry, creatorRepository, chatRoomRepositoryV2,
                chatRoomServiceV2, creatorGoLiveService, null, null);
    }
    
    // Map of session ID to creator email
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    // Map of session ID to user ID (if authenticated)
    private final Map<String, Long> sessionToUserId = new ConcurrentHashMap<>();

    // Map of session ID to creator entity ID (Creator.id)
    private final Map<String, Long> sessionToCreatorId = new ConcurrentHashMap<>();

    // Set of currently connected creator entity IDs for heartbeat refresh
    private final Set<Long> activeCreatorIds = ConcurrentHashMap.newKeySet();

    // In-memory fallback ONLY for session counting when RedisTemplate is not available
    private final Map<Long, Integer> userSessionCount = new ConcurrentHashMap<>();
    
    // Map of session ID to Set of destinations (subscriptions)
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    // Map of session ID to (Subscription ID -> Destination)
    private final Map<String, Map<String, String>> subscriptionToDestination = new ConcurrentHashMap<>();

    // Map of session ID -> Destination -> Join Instant
    private final Map<String, Map<String, Instant>> sessionJoinTimes = new ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String simpSessionId = (String) headerAccessor.getHeader("simpSessionId");
        Principal principal = event.getUser();
        String principalName = (principal != null) ? principal.getName() : "anonymous";
        log.info("SessionConnectEvent: principal={}, sessionId={}, simpSessionId={}", principalName, sessionId, simpSessionId);

        if (principal != null && sessionId != null) {
            String email = principal.getName();
            activeSessions.put(sessionId, email);
            log.info("WebSocket: User {} connected (Session: {})", email, sessionId);
            
            try {
                // Resolve authenticated creator
                Long userId = null;
                Role role = null;
                
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
                
                // Fallback to service if not resolved from principal
                if (userId == null) {
                    User user = userService.getByEmail(email);
                    userId = user.getId();
                    role = user.getRole();
                }
                
                sessionToUserId.put(sessionId, userId);
                
                // Track presence in Redis or fallback
                if (redisTemplate != null) {
                    redisTemplate.opsForValue().increment(USER_SESSION_COUNT_PREFIX + userId);
                    redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId);
                } else {
                    userSessionCount.merge(userId, 1, Integer::sum);
                }
                
                if (role == Role.CREATOR) {
                    final Long userIdFinal = userId;
                    com.joinlivora.backend.creator.model.Creator creatorEntity = creatorRepository.findByUser_Id(userIdFinal)
                            .orElseGet(() -> {
                                User user = userService.getById(userIdFinal);
                                return creatorRepository.save(com.joinlivora.backend.creator.model.Creator.builder()
                                        .user(user)
                                        .active(true)
                                        .build());
                            });
                    
                    Long creatorId = creatorEntity.getId();
                    log.info("CREATOR ONLINE: {} (userId: {})", creatorId, userIdFinal);
                    
                    activeCreatorIds.add(creatorId);
                    sessionToCreatorId.put(sessionId, creatorId);
                    onlineCreatorRegistry.markOnline(creatorId, sessionId);
                    
                    // Unified go-live flow when available; otherwise fallback to legacy inline logic
                    java.util.List<ChatRoom> activatedRooms;
                    if (creatorGoLiveService != null) {
                        activatedRooms = creatorGoLiveService.goLive(creatorId);
                    } else {
                        // Legacy inline behavior (kept for test compatibility)
                        try {
                            // Mark presence online + Redis TTL
                            creatorPresenceService.markOnline(creatorId);
                            if (redisEnabled) {
                                onlineStatusService.setOnline(creatorId);
                            }

                            // Broadcast presence
                            broadcastCreatorPresence(userIdFinal, creatorId);

                            // Activate rooms + broadcast state (legacy path emits only state update for tests)
                            activatedRooms = chatRoomServiceV2.activateWaitingRooms(creatorId);

                            activatedRooms.forEach(room -> {
                                RealtimeMessage msg = RealtimeMessage.builder()
                                        .type("chat:state:update")
                                        .timestamp(Instant.now())
                                        .payload(java.util.Map.of(
                                                "creatorUserId", userIdFinal,
                                                "creator", creatorId,
                                                "roomId", room.getId(),
                                                "status", ChatRoomStatus.ACTIVE.name()
                                        ))
                                        .build();
                                String destination = "/topic/chat/v2/creator/" + userIdFinal + "/status";
                                messagingTemplate.convertAndSend(destination, msg);
                            });
                        } catch (Exception e) {
                            log.error("Legacy go-live flow failed for creator {}: {}", creatorId, e.getMessage());
                            activatedRooms = java.util.Collections.emptyList();
                        }
                    }

                    // Private per-viewer updates for rooms the viewer is subscribed to
                    activatedRooms.forEach(room -> {
                        String chatTopic = "/topic/chat/" + room.getId();
                        java.util.Map<String, Object> statusPayload = java.util.Map.of(
                                "chatRoomId", room.getId(),
                                "creatorUserId", userIdFinal,
                                "creator", creatorId,
                                "status", "ACTIVE"
                        );

                        activeSessions.forEach((sessId, userEmail) -> {
                            java.util.Set<String> subs = sessionSubscriptions.get(sessId);
                            if (subs != null && subs.contains(chatTopic)) {
                                messagingTemplate.convertAndSendToUser(userEmail, "/queue/chat-status", statusPayload);
                                log.debug("Sent private status update to viewer {} for room {}", userEmail, room.getId());
                            }
                        });
                    });
                }
            } catch (Exception e) {
                log.warn("WebSocket: Could not resolve user info for presence: {}. Error: {}", email, e.getMessage());
            }

            broadcastPresence();
            
            // Broadcast join event to admin topic
            messagingTemplate.convertAndSend("/topic/admin/activity", RealtimeMessage.of("user_join", Map.of("email", email)));
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String simpSessionId = (String) headerAccessor.getHeader("simpSessionId");
        Principal principal = event.getUser();
        String principalName = (principal != null) ? principal.getName() : "anonymous";
        log.info("SessionDisconnectEvent: principal={}, sessionId={}, simpSessionId={}", principalName, sessionId, simpSessionId);

        log.debug("WebSocket: Disconnect event received for session: {}", sessionId);
        String email = activeSessions.remove(sessionId);
        
        Long userId = sessionToUserId.remove(sessionId);
        Long creatorId = sessionToCreatorId.remove(sessionId);

        // Fallback for userId if not in session map
        if (userId == null && (email != null || principal != null)) {
            try {
                if (principal instanceof org.springframework.security.core.Authentication auth) {
                    if (auth.getPrincipal() instanceof StompPrincipal sp) {
                        userId = Long.valueOf(sp.getUserId());
                    } else if (auth.getPrincipal() instanceof com.joinlivora.backend.security.UserPrincipal up) {
                        userId = up.getUserId();
                    }
                }
                
                // Fallback to service if not resolved from principal
                if (userId == null && email != null) {
                    userId = userService.getByEmail(email).getId();
                }
            } catch (Exception e) {
                log.debug("WebSocket: Could not resolve userId on disconnect fallback for {}: {}", email, e.getMessage());
            }
        }

        boolean isLastSession = false;
        if (userId != null) {
            if (redisTemplate != null) {
                Long count = redisTemplate.opsForValue().decrement(USER_SESSION_COUNT_PREFIX + userId);
                if (count == null || count <= 0) {
                    isLastSession = true;
                    redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId);
                    redisTemplate.delete(USER_SESSION_COUNT_PREFIX + userId);
                }
            } else {
                Integer count = userSessionCount.computeIfPresent(userId, (k, v) -> v > 1 ? v - 1 : null);
                if (count == null) {
                    isLastSession = true;
                }
            }

            if (isLastSession && creatorId != null && activeCreatorIds.remove(creatorId)) {
                    log.info("CREATOR OFFLINE: {} (userId: {})", creatorId, userId);
                    onlineCreatorRegistry.markOffline(creatorId);
                    creatorPresenceService.markOffline(creatorId);
                    if (redisEnabled) {
                        onlineStatusService.setOffline(creatorId);
                    }
                    broadcastCreatorPresence(userId, creatorId);

                    // V2 Chat: Pause active rooms and notify viewers
                    try {
                        List<ChatRoom> pausedRooms = chatRoomServiceV2.pauseActiveRooms(creatorId);
                        final Long userIdFinal = userId;
                        final Long creatorIdFinal = creatorId;
                        // Emit a creator left event (once on last disconnect)
                        RealtimeMessage leftEvent = RealtimeMessage.of("chat:creator:left", java.util.Map.of(
                                "creatorUserId", userIdFinal,
                                "creator", creatorIdFinal
                        ));
                        messagingTemplate.convertAndSend("/topic/chat/v2/creator/" + userIdFinal + "/status", leftEvent);

                        pausedRooms.forEach(room -> {
                            // 1. Notify subscribers that the room is now paused (Broadcast)
                            RealtimeMessage msg = RealtimeMessage.builder()
                                    .type("chat:state:update")
                                    .timestamp(Instant.now())
                                    .payload(java.util.Map.of(
                                            "creatorUserId", userIdFinal,
                                            "creator", creatorIdFinal,
                                            "roomId", room.getId(),
                                            "status", ChatRoomStatus.PAUSED.name()
                                    ))
                                    .build();
                            String destination = "/topic/chat/v2/creator/" + userIdFinal + "/status";
                            messagingTemplate.convertAndSend(destination, msg);

                            // 2. Notify viewers subscribed to this specific room
                            String chatTopic = "/topic/chat/" + room.getId();
                            java.util.Map<String, Object> statusPayload = java.util.Map.of(
                                    "chatRoomId", room.getId(),
                                    "creatorUserId", userIdFinal,
                                    "creator", creatorIdFinal,
                                    "status", "PAUSED"
                            );

                            activeSessions.forEach((sessId, userEmail) -> {
                                java.util.Set<String> subs = sessionSubscriptions.get(sessId);
                                if (subs != null && subs.contains(chatTopic)) {
                                    messagingTemplate.convertAndSendToUser(userEmail, "/queue/chat-status", statusPayload);
                                    log.debug("Sent private status update (PAUSED) to viewer {} for room {}", userEmail, room.getId());
                                }
                            });
                        });
                    } catch (Exception e) {
                        log.error("Error pausing chat rooms for creator {}: {}", creatorId, e.getMessage());
                    }
                }
                }

        // Cleanup subscription mappings
        subscriptionToDestination.remove(sessionId);
        
        // Cleanup stream viewers on disconnect
        Set<String> destinations = sessionSubscriptions.remove(sessionId);
        if (destinations != null) {
            destinations.forEach(dest -> handleLeaveStream(dest, email, sessionId));
        }
        
        // Cleanup join times
        sessionJoinTimes.remove(sessionId);
        
        if (email != null) {
            log.info("WebSocket: User {} disconnected (Session: {})", email, sessionId);
            broadcastPresence();
            
            // Broadcast leave event to admin topic
            messagingTemplate.convertAndSend("/topic/admin/activity", RealtimeMessage.of("user_leave", Map.of("email", email)));

            // Check if creator went offline - only on last session
            if (isLastSession) {
                streamService.findByCreatorEmail(email).ifPresent(room -> {
                    if (room.isLive()) {
                        ChatMessage systemMessage = ChatMessage.builder()
                                .content("Creator went offline")
                                .system(true)
                                .timestamp(Instant.now())
                                .build();
                        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), RealtimeMessage.ofChat(systemMessage));
                    }
                });
            }
        }
    }

    @EventListener
    public void handleSubscriptionEvent(SessionSubscribeEvent event) {
        Principal principal = event.getUser();
        String destination = (String) event.getMessage().getHeaders().get("simpDestination");
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        String subscriptionId = (String) event.getMessage().getHeaders().get("simpSubscriptionId");
        
        if (destination != null && sessionId != null) {
            // DIAGNOSTICS START
            log.info("event=SUBSCRIBE sessionId={} destination={}", sessionId, destination);
            // DIAGNOSTICS END
            log.debug("Presence: Session {} subscribing to {}", sessionId, destination);
            boolean isNew = sessionSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(destination);
            if (subscriptionId != null) {
                subscriptionToDestination.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(subscriptionId, destination);
            }
            
            if (isNew) {
                handleJoinStream(destination, principal != null ? principal.getName() : "anonymous", sessionId);
            }
        }
        
        if (principal != null && "/topic/premium".equals(destination)) {
            log.debug("User {} subscribed to {}", principal.getName(), destination);
        }
    }

    @EventListener
    public void handleUnsubscribeEvent(SessionUnsubscribeEvent event) {
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        String subscriptionId = (String) event.getMessage().getHeaders().get("simpSubscriptionId");
        
        if (sessionId != null && subscriptionId != null) {
            Map<String, String> subs = subscriptionToDestination.get(sessionId);
            if (subs != null) {
                String destination = subs.remove(subscriptionId);
                if (destination != null) {
                    // DIAGNOSTICS START
                    log.info("event=UNSUBSCRIBE sessionId={} destination={}", sessionId, destination);
                    // DIAGNOSTICS END
                    Set<String> dests = sessionSubscriptions.get(sessionId);
                    boolean removed = false;
                    if (dests != null) {
                        removed = dests.remove(destination);
                    }
                    
                    if (removed) {
                        String email = activeSessions.get(sessionId);
                        handleLeaveStream(destination, email, sessionId);
                    }
                }
            }
        }
    }

    private void handleJoinStream(String destination, String email, String sessionId) {
        UUID streamId = null;
        try {
            if (destination.startsWith("/topic/viewers/")) {
                String streamIdStr = destination.substring("/topic/viewers/".length());
                streamId = UUID.fromString(streamIdStr);
            } else if (destination.startsWith("/topic/stream/") && destination.endsWith("/video")) {
                String idPart = destination.substring("/topic/stream/".length(), destination.length() - "/video".length());
                // Trim possible trailing slash
                if (idPart.endsWith("/")) idPart = idPart.substring(0, idPart.length() - 1);
                streamId = UUID.fromString(idPart);
            }
        } catch (Exception ignored) {
            streamId = null;
        }

        if (streamId != null) {
            try {
                StreamRoom room = streamService.getRoom(streamId);
                User user = (email != null && !"anonymous".equals(email)) ? userService.getByEmail(email) : null;

                log.info("Presence: User {} joined stream viewers {}", email != null ? email : "anonymous", streamId);

                // Update viewer count
                streamService.updateViewerCount(streamId, 1);

                // Broadcast update
                messagingTemplate.convertAndSend("/topic/viewers/" + streamId, Map.of("onlineCount", room.getViewerCount() + 1));

                // Track join time
                sessionJoinTimes.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(destination, Instant.now());

                // Publish analytics event
                analyticsEventPublisher.publishEvent(
                        AnalyticsEventType.STREAM_JOIN,
                        user,
                        Map.of("roomId", streamId, "creator", room.getCreator().getId())
                );
            } catch (Exception e) {
                log.error("Error handling stream join: {}", e.getMessage());
            }
        }
    }

    private void handleLeaveStream(String destination, String email, String sessionId) {
        UUID streamId = null;
        try {
            if (destination.startsWith("/topic/viewers/")) {
                String streamIdStr = destination.substring("/topic/viewers/".length());
                streamId = UUID.fromString(streamIdStr);
            } else if (destination.startsWith("/topic/stream/") && destination.endsWith("/video")) {
                String idPart = destination.substring("/topic/stream/".length(), destination.length() - "/video".length());
                if (idPart.endsWith("/")) idPart = idPart.substring(0, idPart.length() - 1);
                streamId = UUID.fromString(idPart);
            }
        } catch (Exception ignored) {
            streamId = null;
        }

        if (streamId != null) {
            try {
                log.info("Presence: User {} left stream viewers {}", email != null ? email : "anonymous", streamId);

                StreamRoom room = streamService.getRoom(streamId);

                // Update viewer count
                streamService.updateViewerCount(streamId, -1);

                // Broadcast update
                messagingTemplate.convertAndSend("/topic/viewers/" + streamId, Map.of("onlineCount", Math.max(0, room.getViewerCount() - 1)));

                // Calculate duration
                long durationSeconds = 0;
                Map<String, Instant> joins = sessionJoinTimes.get(sessionId);
                if (joins != null) {
                    Instant joinTime = joins.remove(destination);
                    if (joinTime != null) {
                        durationSeconds = Duration.between(joinTime, Instant.now()).getSeconds();
                    }
                }

                // Publish analytics event
                User user = (email != null && !"anonymous".equals(email)) ? userService.getByEmail(email) : null;
                analyticsEventPublisher.publishEvent(
                        AnalyticsEventType.STREAM_LEAVE,
                        user,
                        Map.of("roomId", streamId, "creator", room.getCreator().getId(), "duration", durationSeconds)
                );
            } catch (Exception e) {
                log.error("Error handling stream leave: {}", e.getMessage());
            }
        }
    }


    private void broadcastPresence() {
        // Broadcast unique online users count from Redis
        Long onlineCount = 0L;
        if (redisTemplate != null) {
            Long size = redisTemplate.opsForSet().size(ONLINE_USERS_KEY);
            if (size != null) onlineCount = size;
        }
        messagingTemplate.convertAndSend("/topic/presence", Map.of(
                "onlineCount", onlineCount,
                "timestamp", Instant.now()
        ));
    }

    public boolean isOnline(Long userId) {
        if (userId == null || redisTemplate == null) return false;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, userId));
    }

    private void broadcastCreatorPresence(Long creatorUserId, Long creatorId) {
        if (creatorUserId == null) return;
        com.joinlivora.backend.presence.model.CreatorAvailabilityStatus availability = creatorPresenceService.getAvailability(creatorUserId);
        
        long viewerCount = 0L;
        try {
            if (liveViewerCounterService != null) {
                viewerCount = liveViewerCounterService.getViewerCount(creatorUserId);
            }
        } catch (Exception e) {
            log.warn("PRESENCE: viewerCount lookup failed for creatorUserId={}: {}", creatorUserId, e.getMessage());
        }
        log.info("PRESENCE: Broadcasting availability for creatorUserId {}: {} (viewers: {})", creatorUserId, availability, viewerCount);
        
        RealtimeMessage message = RealtimeMessage.of("presence:update", Map.of(
                "creatorUserId", creatorUserId,
                "creator", creatorId != null ? creatorId : -1L,
                "online", availability != com.joinlivora.backend.presence.model.CreatorAvailabilityStatus.OFFLINE,
                "availability", availability.name(),
                "viewerCount", viewerCount
        ));
        messagingTemplate.convertAndSend("/topic/creators/presence", message);
    }

    public boolean isSubscribedTo(String sessionId, String destination) {
        if (sessionId == null || destination == null) return false;
        Set<String> destinations = sessionSubscriptions.get(sessionId);
        return destinations != null && destinations.contains(destination);
    }

    @Scheduled(fixedDelay = 30000) // Refresh online status every 30 seconds
    public void refreshPresence() {
        if (!activeCreatorIds.isEmpty()) {
            log.trace("Presence: Refreshing online status for {} creators", activeCreatorIds.size());
            activeCreatorIds.forEach(id -> {
                creatorPresenceService.markOnline(id);
                if (redisEnabled) {
                    onlineStatusService.refreshOnlineStatus(id);
                }
            });
        }
    }
}
