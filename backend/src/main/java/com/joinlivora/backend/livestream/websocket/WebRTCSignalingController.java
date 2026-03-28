package com.joinlivora.backend.livestream.websocket;

import com.joinlivora.backend.livestream.domain.LivestreamSession;
import com.joinlivora.backend.livestream.domain.LivestreamStatus;
import com.joinlivora.backend.livestream.repository.LivestreamSessionRepository;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.websocket.WebRtcSessionRegistry;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.LivestreamAccessService;
import com.joinlivora.backend.presence.service.SessionRegistryService;
import com.joinlivora.backend.presence.service.PresenceTrackingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.joinlivora.backend.streaming.client.MediasoupClient;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.HashMap;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.slf4j.MDC;
import com.joinlivora.backend.websocket.RealtimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebRTCSignalingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebRtcSessionRegistry sessionService;
    private final LiveViewerCounterService liveViewerCounterService;
    private final LivestreamAccessService livestreamAccessService;
    private final SessionRegistryService sessionRegistryService;
    private final PresenceTrackingService presenceTrackingService;
    private final LivestreamSessionRepository livestreamSessionRepository;
    private final StreamRepository streamRepository;
    private final UserService userService;
    private final MediasoupClient mediasoupClient;

    @Value("${livora.security.max-sessions-per-ip:10}")
    private int maxSessionsPerIp;

    @Value("${livora.security.max-global-sessions:5000}")
    private int maxGlobalSessions;

    @Value("${livora.security.max-joins-per-window:100}")
    private int maxJoinsPerWindow;

    @Value("${livora.security.join-window-ms:5000}")
    private int joinWindowMs;

    private final Map<String, RateLimitState> signalingRateLimits = new ConcurrentHashMap<>();
    private final Map<String, RateLimitState> ipRateLimits = new ConcurrentHashMap<>();
    private final Map<String, BucketWrapper> roomJoinBuckets = new ConcurrentHashMap<>();
    private final Map<String, Integer> ipSessionCounts = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdToIp = new ConcurrentHashMap<>();
    private final AtomicInteger globalSessionCount = new AtomicInteger(0);

    @Autowired
    @Qualifier("webrtcTaskExecutor")
    private Executor taskExecutor;

    @Autowired
    @Lazy
    private WebRTCSignalingController self;

    @Data
    @Builder
    public static class SignalContext {
        private String type;
        private String roomId;
        private String signalingRoomId;
        private Long currentUserId;
        private Long creatorId;
        private LivestreamSession session;
        private boolean isMediasoup;
        private String principalName;
        private String sessionId;
        private SignalingMessage message;
    }

    private static class RateLimitState {
        int count;
        long windowStartTime;
        String ipAddress;

        RateLimitState(long startTime, String ipAddress) {
            this.windowStartTime = startTime;
            this.count = 1;
            this.ipAddress = ipAddress;
        }
    }

    private static class BucketWrapper {
        final Bucket bucket;
        final AtomicLong lastUsed;

        BucketWrapper(Bucket bucket) {
            this.bucket = bucket;
            this.lastUsed = new AtomicLong(System.currentTimeMillis());
        }

        void markUsed() {
            this.lastUsed.set(System.currentTimeMillis());
        }
    }

    @MessageMapping("webrtc.signal")
    public void handleSignal(SignalingMessage message, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
        String corrId = (headerAccessor.getSessionAttributes() != null) ? 
            (String) headerAccessor.getSessionAttributes().get("corrId") : null;
        
        if (corrId == null) {
            corrId = java.util.UUID.randomUUID().toString();
            if (headerAccessor.getSessionAttributes() != null) {
                headerAccessor.getSessionAttributes().put("corrId", corrId);
            }
        }
        
        final String finalCorrId = corrId;
        final Map<String, String> contextMap = MDC.getCopyOfContextMap();
        
        CompletableFuture.runAsync(() -> {
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }
            MDC.put("corrId", finalCorrId);
            try {
                self.processSignal(message, headerAccessor, principal);
            } finally {
                MDC.clear();
            }
        }, taskExecutor);
    }

    public void processSignal(SignalingMessage message, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
        String principalName = (principal != null) ? principal.getName() : "anonymous";
        String sessionId = headerAccessor.getSessionId();
        String type = message.getType();

        log.info("WEBRTC TRACE: received signal type={}, roomId={}, sessionId={}, user={}", 
                type, message.getRoomId(), sessionId, principalName);

        // 0. Rate limiting check (30 messages / 10 seconds)
        if (isRateLimited(sessionId, principalName, message, headerAccessor)) {
            org.slf4j.MDC.remove("corrId");
            return;
        }

        // 0.5. Join rate protection (100 joins / 5 seconds per room)
        if ("JOIN".equalsIgnoreCase(type)) {
            if (isJoinRateLimited(message, principalName)) {
                org.slf4j.MDC.remove("corrId");
                return;
            }
        }

        log.info("WEBRTC-SIGNAL PROCESSING: type={}, principal={}, sessionId={}",
                type, principalName, sessionId);

        // 1. Load and validate context (Transactional)
        SignalContext context;
        try {
            context = self.loadSignalContext(message, headerAccessor, principal);
        } catch (AccessDeniedException e) {
            log.error("SECURITY VIOLATION: {} during signaling. terminating session.", e.getMessage());
            
            // Send signaling error
            messagingTemplate.convertAndSendToUser(principalName, "/queue/errors",
                    SignalingMessage.error("UNAUTHORIZED_PRODUCE", null, message.getRoomId(), message.getStreamId()));
            
            // Send disconnect message to trigger client-side closure and prevent further probing
            RealtimeMessage disconnectMessage = RealtimeMessage.builder()
                    .type("DISCONNECT")
                    .payload(Map.of("reason", "Security violation: unauthorized produce attempt."))
                    .timestamp(Instant.now())
                    .build();
            messagingTemplate.convertAndSendToUser(principalName, "/queue/notifications", disconnectMessage);
            
            org.slf4j.MDC.remove("corrId");
            return;
        }

        if (context == null) {
            org.slf4j.MDC.remove("corrId");
            return;
        }

        // 2. Mediasoup SFU Signaling (Non-transactional - runs on dedicated mediasoupExecutor)
        if (context.isMediasoup()) {
            final Map<String, String> mdcContext = MDC.getCopyOfContextMap();
            processMediasoupSignaling(context).thenAccept(result -> {
                if (mdcContext != null) MDC.setContextMap(mdcContext);
                try {
                    if (result == null) {
                        return;
                    }
                    // 3. Finalize state and relay (Transactional)
                    self.finalizeSignalState(context, result, headerAccessor);
                } finally {
                    MDC.clear();
                }
            });
        } else {
            // 3. Finalize state and relay (Transactional)
            self.finalizeSignalState(context, message, headerAccessor);
            org.slf4j.MDC.remove("corrId");
        }
    }

    private boolean isRateLimited(String sessionId, String principalName, SignalingMessage message, SimpMessageHeaderAccessor headerAccessor) {
        if (sessionId == null) return false;

        final String ip = (headerAccessor.getSessionAttributes() != null) ? 
                (String) headerAccessor.getSessionAttributes().get("ip") : null;

        long now = System.currentTimeMillis();

        // 1. Per-session limit
        RateLimitState sessionState = signalingRateLimits.compute(sessionId, (sid, oldState) -> {
            if (oldState == null || now - oldState.windowStartTime > 10000) {
                return new RateLimitState(now, ip);
            } else {
                oldState.count++;
                return oldState;
            }
        });

        if (sessionState.count > 30) {
            log.warn("SESSION RATE LIMIT VIOLATION: sessionId={}, principal={}, messageType={}, count={}",
                    sessionId, principalName, message.getType(), sessionState.count);
            handleRateLimitViolation(principalName, message);
            return true;
        }

        // 2. Per-IP limit
        if (ip != null) {
            RateLimitState ipState = ipRateLimits.compute(ip, (k, oldState) -> {
                if (oldState == null || now - oldState.windowStartTime > 10000) {
                    return new RateLimitState(now, k);
                } else {
                    oldState.count++;
                    return oldState;
                }
            });

            if (ipState.count > 30) {
                log.warn("IP RATE LIMIT VIOLATION: ip={}, principal={}, messageType={}, count={}",
                        ip, principalName, message.getType(), ipState.count);
                handleRateLimitViolation(principalName, message);
                return true;
            }
        }

        return false;
    }

    private boolean isJoinRateLimited(SignalingMessage message, String principalName) {
        String roomId = message.getRoomId();
        if (roomId == null || roomId.isBlank()) return false;

        BucketWrapper wrapper = roomJoinBuckets.computeIfAbsent(roomId, k -> {
            Bandwidth limit = Bandwidth.builder()
                .capacity(maxJoinsPerWindow)
                .refillGreedy(maxJoinsPerWindow, Duration.ofMillis(joinWindowMs))
                .build();
            return new BucketWrapper(Bucket.builder().addLimit(limit).build());
        });

        wrapper.markUsed();

        if (!wrapper.bucket.tryConsume(1)) {
            log.warn("JOIN RATE LIMIT VIOLATION for room {}: principal={}. Rejecting join request.",
                    roomId, principalName);
            handleRateLimitViolation(principalName, message);
            return true;
        }

        return false;
    }

    @Scheduled(fixedRate = 600000) // Every 10 minutes
    public void cleanupRateLimits() {
        long now = System.currentTimeMillis();
        signalingRateLimits.entrySet().removeIf(entry -> now - entry.getValue().windowStartTime > 60000);
        ipRateLimits.entrySet().removeIf(entry -> now - entry.getValue().windowStartTime > 60000);
        roomJoinBuckets.entrySet().removeIf(entry -> now - entry.getValue().lastUsed.get() > joinWindowMs * 2);
        log.debug("Cleaned up signaling rate limits maps. Current sizes: signaling={}, ip={}, rooms={}", 
                signalingRateLimits.size(), ipRateLimits.size(), roomJoinBuckets.size());
    }

    private void handleRateLimitViolation(String principalName, SignalingMessage message) {
        // Send signaling error
        messagingTemplate.convertAndSendToUser(principalName, "/queue/errors",
                SignalingMessage.error("RATE_LIMIT_EXCEEDED", null, message.getRoomId(), message.getStreamId()));

        // Send disconnect message
        RealtimeMessage disconnectMessage = RealtimeMessage.builder()
                .type("DISCONNECT")
                .payload(Map.of("reason", "Signaling rate limit exceeded."))
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSendToUser(principalName, "/queue/notifications", disconnectMessage);
    }

    private void handleSessionLimitViolation(String principalName, SignalingMessage message) {
        // Send signaling error
        messagingTemplate.convertAndSendToUser(principalName, "/queue/errors",
                SignalingMessage.error("SESSION_LIMIT_EXCEEDED", null, message.getRoomId(), message.getStreamId()));

        // Send disconnect message
        RealtimeMessage disconnectMessage = RealtimeMessage.builder()
                .type("DISCONNECT")
                .payload(Map.of("reason", "Too many active sessions from your IP."))
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSendToUser(principalName, "/queue/notifications", disconnectMessage);
    }

    private void handleGlobalSessionLimitViolation(String principalName, SignalingMessage message) {
        // Send signaling error
        messagingTemplate.convertAndSendToUser(principalName, "/queue/errors",
                SignalingMessage.error("GLOBAL_SESSION_LIMIT_EXCEEDED", null, message.getRoomId(), message.getStreamId()));

        // Send disconnect message
        RealtimeMessage disconnectMessage = RealtimeMessage.builder()
                .type("DISCONNECT")
                .payload(Map.of("reason", "System is at maximum signaling capacity. Please try again later."))
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSendToUser(principalName, "/queue/notifications", disconnectMessage);
    }

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        if (sessionId == null) return;

        String ip = null;
        if (headers.getSessionAttributes() != null) {
            ip = (String) headers.getSessionAttributes().get("ip");
        }

        if (globalSessionCount.get() >= maxGlobalSessions) {
            log.warn("GLOBAL SESSION LIMIT VIOLATION on CONNECT: current={}, limit={}, sessionId={}", 
                    globalSessionCount.get(), maxGlobalSessions, sessionId);
            throw new RuntimeException("GLOBAL_SESSION_LIMIT_EXCEEDED");
        }

        if (ip != null) {
            int currentSessions = ipSessionCounts.getOrDefault(ip, 0);
            if (currentSessions >= maxSessionsPerIp) {
                log.warn("MAX SESSIONS PER IP VIOLATION on CONNECT: ip={}, current={}, limit={}, sessionId={}", 
                        ip, currentSessions, maxSessionsPerIp, sessionId);
                throw new RuntimeException("SESSION_LIMIT_EXCEEDED");
            }
            ipSessionCounts.merge(ip, 1, Integer::sum);
            sessionIdToIp.put(sessionId, ip);
        }

        globalSessionCount.incrementAndGet();
        log.debug("Session connected: {}. Global count: {}", sessionId, globalSessionCount.get());
    }

    /**
     * Cleans up rate-limiting and session-counting state local to this controller.
     * Called by WebSocketSessionCleanupService during centralized disconnect handling.
     */
    public void cleanupSessionRateLimiting(String sessionId) {
        if (sessionId == null) return;

        signalingRateLimits.remove(sessionId);
        String ip = sessionIdToIp.remove(sessionId);
        if (ip != null) {
            ipSessionCounts.compute(ip, (k, v) -> (v == null || v <= 1) ? null : v - 1);
        }
        globalSessionCount.decrementAndGet();
        log.debug("Session disconnected: {}. Global count: {}", sessionId, globalSessionCount.get());
    }

    @Transactional(readOnly = true)
    public SignalContext loadSignalContext(SignalingMessage message, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
        String type = message.getType();
        String principalName = (principal != null) ? principal.getName() : "anonymous";
        
        // Identify current user
        Long currentUserId = null;
        if (principal != null) {
            currentUserId = userService.resolveUserFromSubject(principal.getName())
                    .map(User::getId)
                    .orElse(null);
        }

        // Identify the room and creator
        String rid = message.getRoomId();
        String signalingRoomId = rid;
        Long creatorId = null;

        if (rid != null && !rid.isBlank()) {
            try {
                UUID ridUuid = UUID.fromString(rid);
                creatorId = streamRepository.findById(ridUuid)
                        .map(stream -> stream.getCreator().getId())
                        .orElseGet(() -> streamRepository.findByMediasoupRoomId(ridUuid)
                                .map(stream -> stream.getCreator().getId())
                                .orElse(null));
            } catch (IllegalArgumentException e) {
                log.error("Invalid roomId UUID: {}", rid);
            }
        }

        // Fallback for creator self-identification
        if (creatorId == null && currentUserId != null) {
            if (userService.getById(currentUserId).getRole() == com.joinlivora.backend.user.Role.CREATOR) {
                creatorId = currentUserId;
            }
        }

        // Prefer canonical roomId
        if (creatorId != null) {
            java.util.List<Stream> activeStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorId);
            if (!activeStreams.isEmpty()) {
                UUID newRidUuid = activeStreams.get(0).getMediasoupRoomId();
                if (newRidUuid != null) {
                    rid = newRidUuid.toString();
                }
            }
        }

        // Identify active session
        Optional<LivestreamSession> sessionOpt = Optional.empty();
        if (creatorId != null) {
            sessionOpt = livestreamSessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(creatorId, LivestreamStatus.LIVE);
        }
        LivestreamSession session = sessionOpt.orElse(null);

        // Access validation
        boolean isMediasoup = type != null && (
            "GET_ROUTER_CAPABILITIES".equalsIgnoreCase(type) || 
            "CREATE_TRANSPORT".equalsIgnoreCase(type) ||
            "CONNECT_TRANSPORT".equalsIgnoreCase(type) ||
            "PRODUCE".equalsIgnoreCase(type) ||
            "CONSUME".equalsIgnoreCase(type) ||
            "RESUME_CONSUMER".equalsIgnoreCase(type) ||
            "JOIN".equalsIgnoreCase(type) ||
            "RESTART_ICE".equalsIgnoreCase(type)
        );

        boolean isLegacyWebrtc = "offer".equalsIgnoreCase(type) || "answer".equalsIgnoreCase(type);
        boolean isGetRouterCaps = "GET_ROUTER_CAPABILITIES".equalsIgnoreCase(type);

        if (isMediasoup || isLegacyWebrtc) {
            // GET_ROUTER_CAPABILITIES is exempted from the LIVE session requirement
            // to resolve a race condition where the client requests capabilities 
            // before the session is fully marked as LIVE in the database.
            if (!isGetRouterCaps && (session == null || !session.isLive())) {
                log.warn("WEBRTC-SIGNAL BLOCKED: No active LIVE session for type={}, creatorId={}, roomId={}", type, creatorId, rid);
                return null;
            }

            // Strengthen producer authorization: Only the creator can publish
            if ("PRODUCE".equalsIgnoreCase(type)) {
                if (currentUserId == null || !currentUserId.equals(session.getCreator().getId())) {
                    log.warn("SECURITY AUDIT: Unauthorized PRODUCE attempt for roomId {} by user {}", rid, currentUserId);
                    throw new AccessDeniedException("Only the stream creator may publish media");
                }
            }

            // Check access for both authenticated and anonymous viewers
            // LivestreamAccessService.hasAccess handles the 'free stream' logic correctly
            if (session != null && !livestreamAccessService.hasAccess(session.getId(), currentUserId)) {
                log.warn("WEBRTC-SIGNAL BLOCKED: No access for viewer {} to session {} (type={})", 
                        currentUserId != null ? currentUserId : "anonymous", session.getId(), type);
                messagingTemplate.convertAndSendToUser(principalName, "/queue/errors",
                        SignalingMessage.error("PAID_ACCESS_REQUIRED", currentUserId, rid, message.getStreamId()));
                return null;
            }
        }

        return SignalContext.builder()
                .type(type)
                .roomId(rid)
                .signalingRoomId(signalingRoomId)
                .currentUserId(currentUserId)
                .creatorId(creatorId)
                .session(session)
                .isMediasoup(isMediasoup)
                .principalName(principalName)
                .sessionId(headerAccessor.getSessionId())
                .message(message)
                .build();
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SignalingMessage> processMediasoupSignaling(SignalContext context) {
        String type = context.getType();
        String rid = context.getRoomId();
        SignalingMessage message = context.getMessage();

        Map<String, Object> responseData = new HashMap<>();
        String requestId = message.getRequestId();
        if (requestId == null && message.getData() instanceof Map) {
            Map<String, Object> reqData = (Map<String, Object>) message.getData();
            Object nestedId = reqData.get("requestId");
            if (nestedId instanceof String) {
                requestId = (String) nestedId;
            }
        }
        if (requestId != null) {
            responseData.put("requestId", requestId);
        }

        CompletableFuture<?> asyncChain;

        if ("GET_ROUTER_CAPABILITIES".equalsIgnoreCase(type)) {
            log.info("WEBRTC TRACE: requesting router capabilities for roomId={}", rid);
            asyncChain = mediasoupClient.getRouterCapabilities(rid).thenAccept(capabilities -> {
                if (capabilities == null) {
                    log.warn("WEBRTC TRACE ERROR: router capabilities returned null");
                } else {
                    log.info("WEBRTC TRACE: router capabilities received for roomId={} payload={}", rid, capabilities);
                    responseData.putAll(capabilities);
                }
            });
        } else if ("JOIN".equalsIgnoreCase(type)) {
            log.info("WEBRTC TRACE: requesting router capabilities for roomId={}", rid);
            asyncChain = mediasoupClient.getRouterCapabilities(rid).thenCompose(capabilities -> {
                if (capabilities == null) {
                    log.warn("WEBRTC TRACE ERROR: router capabilities returned null");
                } else {
                    log.info("WEBRTC TRACE: router capabilities received for roomId={} payload={}", rid, capabilities);
                    responseData.put("routerRtpCapabilities", capabilities);
                }
                return mediasoupClient.getProducers(rid);
            }).thenAccept(producers -> {
                if (producers != null) {
                    responseData.putAll(producers);
                }
            });
        } else if ("CREATE_TRANSPORT".equalsIgnoreCase(type)) {
            asyncChain = mediasoupClient.createTransport(rid).thenAccept(transportOptions -> {
                if (transportOptions != null) {
                    responseData.putAll(transportOptions);
                }
            });
        } else if ("CONNECT_TRANSPORT".equalsIgnoreCase(type)) {
            Map<String, Object> data = (Map<String, Object>) message.getData();
            asyncChain = mediasoupClient.connectTransport(rid, (String) data.get("transportId"), data.get("dtlsParameters")).thenRun(() -> {
                responseData.put("success", true);
            });
        } else if ("CONSUME".equalsIgnoreCase(type)) {
            asyncChain = mediasoupClient.consume(rid, (Map<String, Object>) message.getData()).thenAccept(consumerData -> {
                if (consumerData != null) {
                    responseData.putAll(consumerData);
                }
            });
        } else if ("RESUME_CONSUMER".equalsIgnoreCase(type)) {
            Map<String, Object> data = (Map<String, Object>) message.getData();
            asyncChain = mediasoupClient.resumeConsumer(rid, (String) data.get("consumerId")).thenRun(() -> {
                responseData.put("success", true);
            });
        } else if ("PRODUCE".equalsIgnoreCase(type)) {
            asyncChain = mediasoupClient.produce(rid, (Map<String, Object>) message.getData()).thenAccept(producerData -> {
                if (producerData != null) {
                    responseData.putAll(producerData);

                    // Broadcast NEW_PRODUCER
                    SignalingMessage notification = new SignalingMessage();
                    notification.setType("NEW_PRODUCER");
                    notification.setRoomId(context.getSignalingRoomId());
                    Map<String, Object> notifyData = new HashMap<>();
                    notifyData.put("producerId", producerData.get("id"));
                    notifyData.put("kind", producerData.get("kind"));
                    notification.setData(notifyData);

                    messagingTemplate.convertAndSend("/exchange/amq.topic/webrtc.room." + context.getSignalingRoomId(), notification);
                }
            });
        } else if ("RESTART_ICE".equalsIgnoreCase(type)) {
            if (message.getData() instanceof Map) {
                Map<String, Object> reqData = (Map<String, Object>) message.getData();
                String transportId = (String) reqData.get("transportId");
                asyncChain = mediasoupClient.restartIce(rid, transportId).thenAccept(iceParameters -> {
                    responseData.put("iceParameters", iceParameters);
                });
            } else {
                asyncChain = CompletableFuture.completedFuture(null);
            }
        } else {
            asyncChain = CompletableFuture.completedFuture(null);
        }

        return asyncChain.thenApply(ignored -> {
            message.setData(responseData);
            return message;
        }).exceptionally(e -> {
            log.error("Mediasoup signaling error [{}]: {}", type, e.getMessage(), e);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public void finalizeSignalState(SignalContext context, SignalingMessage result, SimpMessageHeaderAccessor headerAccessor) {
        String type = context.getType();
        Long creatorId = context.getCreatorId();
        Long currentUserId = context.getCurrentUserId();
        String sessionId = context.getSessionId();
        String signalingRoomId = context.getSignalingRoomId();

        if ("CREATE_TRANSPORT".equalsIgnoreCase(type) && result.getData() instanceof Map) {
            String transportId = (String) ((Map<?, ?>) result.getData()).get("id");
            if (transportId != null && headerAccessor.getSessionAttributes() != null) {
                java.util.Set<String> transports = (java.util.Set<String>) headerAccessor.getSessionAttributes()
                        .getOrDefault("mediasoupTransports", new java.util.HashSet<String>());
                transports.add(transportId);
                headerAccessor.getSessionAttributes().put("mediasoupTransports", transports);
                headerAccessor.getSessionAttributes().put("mediasoupRoomId", signalingRoomId);
            }
        }

        if ("LEAVE".equalsIgnoreCase(type)) {
            if (creatorId != null) {
                String ip = null;
                String userAgent = null;
                if (headerAccessor.getSessionAttributes() != null) {
                    ip = (String) headerAccessor.getSessionAttributes().get("ip");
                    userAgent = (String) headerAccessor.getSessionAttributes().get("userAgent");
                }
                liveViewerCounterService.removeViewer(context.getSession().getId(), creatorId, currentUserId, sessionId, ip, userAgent);
                if (headerAccessor.getSessionAttributes() != null) {
                    headerAccessor.getSessionAttributes().remove("creatorUserId");
                }
            }
            return;
        }

        if ("JOIN".equalsIgnoreCase(type)) {
            if (context.getPrincipalName().equals("anonymous")) {
                log.error("JOIN received without principal. Aborting.");
                return;
            }
            
            result.setSenderId(currentUserId);

            if (!sessionService.isViewerAlreadyConnected(sessionId, currentUserId)) {
                sessionService.registerViewer(sessionId, currentUserId);
            }

            if (creatorId != null && currentUserId != null) {
                Long existingCreatorId = null;
                if (headerAccessor.getSessionAttributes() != null) {
                    existingCreatorId = (Long) headerAccessor.getSessionAttributes().get("creatorUserId");
                }

                if (!creatorId.equals(existingCreatorId)) {
                    String ip = null;
                    String userAgent = null;
                    if (headerAccessor.getSessionAttributes() != null) {
                        ip = (String) headerAccessor.getSessionAttributes().get("ip");
                        userAgent = (String) headerAccessor.getSessionAttributes().get("userAgent");
                    }
                    if (existingCreatorId != null) {
                        liveViewerCounterService.removeViewer(context.getSession().getId(), existingCreatorId, currentUserId, sessionId, ip, userAgent);
                    }

                    if (!creatorId.equals(currentUserId)) {
                        // Register session in the comprehensive registry for disconnect cleanup
                        sessionRegistryService.registerSession(sessionId, context.getPrincipalName(), currentUserId, creatorId, ip, userAgent);
                        sessionRegistryService.markStreamJoined(sessionId, context.getSession().getId());
                        presenceTrackingService.markUserOnline(currentUserId);

                        liveViewerCounterService.addViewer(context.getSession().getId(), creatorId, currentUserId, sessionId, ip, userAgent);
                        if (headerAccessor.getSessionAttributes() != null) {
                            headerAccessor.getSessionAttributes().put("creatorUserId", creatorId);
                            headerAccessor.getSessionAttributes().put("userId", currentUserId);
                            headerAccessor.getSessionAttributes().put("streamSessionId", context.getSession().getId());
                        }
                    }
                }
            }
        }

        // Final relay — send response only to the requesting user (not broadcast)
        String principalName = context.getPrincipalName();
        if (principalName != null && !principalName.equals("anonymous")) {
            log.info("WEBRTC TRACE: sending {} response to user {} via /queue/webrtc", type, principalName);
            messagingTemplate.convertAndSendToUser(principalName, "/queue/webrtc", result);
        } else {
            log.warn("WEBRTC TRACE: cannot send response for type={}, no authenticated principal", type);
        }
    }
}
