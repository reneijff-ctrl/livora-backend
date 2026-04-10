package com.joinlivora.backend.streaming;

import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.resilience.DatabaseCircuitBreakerService;
import com.joinlivora.backend.resilience.RedisCircuitBreakerService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.livestream.event.StreamEndedEventV2;
import com.joinlivora.backend.livestream.event.StreamStartedEventV2;
import com.joinlivora.backend.streaming.dto.GoLiveRequest;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.StreamCacheService;
import com.joinlivora.backend.fraud.service.FraudRiskScoreService;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import com.joinlivora.backend.websocket.ChatMessage;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StreamService {

    private final com.joinlivora.backend.user.UserRepository userRepository;
    private final com.joinlivora.backend.livestream.service.LiveStreamService liveStreamService;
    private final ChatRoomService chatRoomService;
    private final UserService userService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final CreatorGoLiveService creatorGoLiveService;
    private final com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository;
    private final com.joinlivora.backend.token.TokenService tokenService;
    private final LiveViewerCounterService liveViewerCounterService;
    private final CreatorVerificationRepository creatorVerificationRepository;
    private final AdminRealtimeEventService adminRealtimeEventService;
    private final StreamRepository streamRepository;
    private final FraudRiskScoreService fraudRiskScoreService;
    private final StreamAssistantBotService streamAssistantBotService;
    private final StreamCacheService streamCacheService;
    private final DatabaseCircuitBreakerService dbCircuitBreaker;
    private final RedisCircuitBreakerService redisCircuitBreaker;

    public StreamService(
            com.joinlivora.backend.user.UserRepository userRepository,
            com.joinlivora.backend.livestream.service.LiveStreamService liveStreamService,
            ChatRoomService chatRoomService,
            UserService userService,
            AnalyticsEventPublisher analyticsEventPublisher,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            CreatorGoLiveService creatorGoLiveService,
            com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository,
            com.joinlivora.backend.token.TokenService tokenService,
            LiveViewerCounterService liveViewerCounterService,
            CreatorVerificationRepository creatorVerificationRepository,
            AdminRealtimeEventService adminRealtimeEventService,
            StreamRepository streamRepository,
            @org.springframework.context.annotation.Lazy FraudRiskScoreService fraudRiskScoreService,
            StreamAssistantBotService streamAssistantBotService,
            StreamCacheService streamCacheService,
            DatabaseCircuitBreakerService dbCircuitBreaker,
            RedisCircuitBreakerService redisCircuitBreaker) {
        this.userRepository = userRepository;
        this.liveStreamService = liveStreamService;
        this.chatRoomService = chatRoomService;
        this.userService = userService;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.messagingTemplate = messagingTemplate;
        this.creatorGoLiveService = creatorGoLiveService;
        this.creatorRepository = creatorRepository;
        this.tokenService = tokenService;
        this.liveViewerCounterService = liveViewerCounterService;
        this.creatorVerificationRepository = creatorVerificationRepository;
        this.adminRealtimeEventService = adminRealtimeEventService;
        this.streamRepository = streamRepository;
        this.fraudRiskScoreService = fraudRiskScoreService;
        this.streamAssistantBotService = streamAssistantBotService;
        this.streamCacheService = streamCacheService;
        this.dbCircuitBreaker = dbCircuitBreaker;
        this.redisCircuitBreaker = redisCircuitBreaker;
    }

    public boolean isStreamLive(UUID streamId) {
        return dbCircuitBreaker.execute(
                () -> streamRepository.findById(streamId).map(Stream::isLive).orElse(false),
                false,
                "isStreamLive");
    }

    public List<StreamRoom> getLiveStreams() {
        // Cache-first: try streams:live ZSET in Redis
        List<UUID> cachedIds = streamCacheService.getLiveStreamIds(50);
        if (!cachedIds.isEmpty()) {
            log.debug("StreamCache: explore page served from Redis ZSET ({} streams)", cachedIds.size());
            List<StreamRoom> rooms = new ArrayList<>();
            for (UUID streamId : cachedIds) {
                // Try per-stream data cache first, fall back to DB
                StreamCacheDTO cached = streamCacheService.getStreamData(streamId);
                if (cached != null) {
                    rooms.add(mapCacheDtoToStreamRoom(cached));
                } else {
                    @SuppressWarnings("unchecked")
                    java.util.Optional<Stream> s = (java.util.Optional<Stream>) dbCircuitBreaker.execute(
                            () -> streamRepository.findByIdWithCreator(streamId),
                            java.util.Optional.empty(),
                            "findStreamByIdForExplore");
                    s.map(this::mapToStreamRoom).ifPresent(rooms::add);
                }
            }
            return rooms;
        }
        // Cache miss — fall back to DB
        log.debug("StreamCache: explore page MISS — querying DB");
        List<Stream> activeStreams = dbCircuitBreaker.execute(
                streamRepository::findActiveStreamsWithUser,
                java.util.Collections.emptyList(),
                "findActiveStreams");
        return activeStreams.stream()
                .map(this::mapToStreamRoom)
                .collect(Collectors.toList());
    }

    private StreamRoom mapCacheDtoToStreamRoom(StreamCacheDTO dto) {
        long viewerCount = liveViewerCounterService.getViewerCount(dto.getCreatorUserId());
        return StreamRoom.builder()
                .id(dto.getId())
                .isLive(dto.isLive())
                .streamTitle(dto.getTitle())
                .isPaid(dto.getAdmissionPrice() != null && dto.getAdmissionPrice().compareTo(java.math.BigDecimal.ZERO) > 0)
                .admissionPrice(dto.getAdmissionPrice())
                .thumbnailUrl(dto.getThumbnailUrl())
                .viewerCount((int) viewerCount)
                .startedAt(dto.getStartedAt())
                .build();
    }

    public StreamRoom getRoom(UUID id) {
        @SuppressWarnings("unchecked")
        java.util.Optional<Stream> streamOpt = (java.util.Optional<Stream>) dbCircuitBreaker.execute(
                () -> streamRepository.findByIdWithCreator(id),
                java.util.Optional.empty(),
                "getRoom");
        return streamOpt
                .map(this::mapToStreamRoom)
                .orElseThrow(() -> new RuntimeException("Stream not found: " + id));
    }

    public java.util.Optional<StreamRoom> findByCreatorEmail(String email) {
        @SuppressWarnings("unchecked")
        java.util.Optional<com.joinlivora.backend.user.User> userOpt =
                (java.util.Optional<com.joinlivora.backend.user.User>) dbCircuitBreaker.execute(
                        () -> userRepository.findByEmail(email),
                        java.util.Optional.empty(),
                        "findUserByEmail");
        return userOpt.map(this::getCreatorRoom);
    }

    public java.util.Optional<StreamRoom> findByCreatorId(Long id) {
        @SuppressWarnings("unchecked")
        java.util.Optional<com.joinlivora.backend.user.User> userOpt =
                (java.util.Optional<com.joinlivora.backend.user.User>) dbCircuitBreaker.execute(
                        () -> userRepository.findById(id),
                        java.util.Optional.empty(),
                        "findUserById");
        return userOpt.map(this::getCreatorRoom);
    }

    public StreamRoom getCreatorRoom(User creator) {
        // Synthesize a room from the unified Stream if possible for compatibility
        List<Stream> liveStreams = streamRepository.findAllByCreatorAndIsLiveTrueOrderByStartedAtDesc(creator);
        if (!liveStreams.isEmpty()) {
            return mapToStreamRoom(liveStreams.get(0));
        }
        return StreamRoom.builder()
                .creator(creator)
                .isLive(false)
                .viewerCount(0)
                .build();
    }

    private StreamRoom mapToStreamRoom(Stream s) {
        return StreamRoom.builder()
                .id(s.getId())
                .creator(s.getCreator())
                .isLive(s.isLive())
                .streamTitle(s.getTitle())
                .description(s.getStreamCategory()) // Category as placeholder
                .isPaid(s.isPaid())
                .admissionPrice(s.getAdmissionPrice())
                .thumbnailUrl(s.getThumbnailUrl())
                .viewerCount((int) liveViewerCounterService.getViewerCount(s.getCreator().getId()))
                .chatEnabled(s.isChatEnabled())
                .slowMode(s.isSlowMode())
                .slowModeInterval(s.getSlowModeInterval())
                .maxViewers(s.getMaxViewers())
                .createdAt(s.getCreatedAt())
                .startedAt(s.getStartedAt())
                .build();
    }

    @Transactional
    public StreamRoom startStream(User creator, String title, String description, Long minChatTokens, boolean isPaid, Long pricePerMessage, BigDecimal admissionPrice, boolean recordingEnabled) {
        if (creator.getStatus() == com.joinlivora.backend.user.UserStatus.SUSPENDED || 
            creator.getStatus() == com.joinlivora.backend.user.UserStatus.TERMINATED) {
            log.warn("SECURITY: Stream start denied for SUSPENDED/TERMINATED creator: {}", creator.getEmail());
            throw new org.springframework.security.access.AccessDeniedException("Account is restricted.");
        }

        if (!creator.isPayoutsEnabled()) {
            log.warn("SECURITY: Stream start denied for creator: {}. Stripe onboarding incomplete.", creator.getEmail());
            throw new org.springframework.security.access.AccessDeniedException("Stripe onboarding incomplete");
        }

        // Identity Verification Check
        creatorVerificationRepository.findByCreatorId(creator.getId())
                .ifPresentOrElse(verification -> {
                    if (verification.getStatus() != VerificationStatus.APPROVED) {
                        log.warn("SECURITY: Stream start denied for unverified creator: {}. Status: {}", creator.getEmail(), verification.getStatus());
                        throw new org.springframework.security.access.AccessDeniedException("Identity verification is " + verification.getStatus() + ". Must be APPROVED to go live.");
                    }
                }, () -> {
                    log.warn("SECURITY: Stream start denied for unverified creator: {}. No verification record found.", creator.getEmail());
                    throw new org.springframework.security.access.AccessDeniedException("Identity verification required to go live.");
                });
        
        StreamRoom room = getCreatorRoom(creator);
        room.setStreamTitle(title);
        room.setDescription(description);
        room.setMinChatTokens(minChatTokens);
        room.setPaid(isPaid);
        room.setPricePerMessage(pricePerMessage);
        room.setAdmissionPrice(isPaid && admissionPrice != null ? admissionPrice : BigDecimal.ZERO);
        // We don't mark as LIVE here anymore, as it will be done when OBS connects
        // room.setLive(false); // Removed as per instructions to unify state management
        room.setStartedAt(null);
        room.setEndedAt(null);
        
        log.info("STREAM: Creator {} prepared stream with title: '{}'. Waiting for OBS.", creator.getEmail(), title);
        StreamRoom saved = room;
        
        // Unified go-live flow
        creatorRepository.findByUser_Id(creator.getId()).ifPresent(c -> {
            GoLiveRequest request = GoLiveRequest.builder()
                    .title(title)
                    .description(description)
                    .minChatTokens(minChatTokens)
                    .isPaid(isPaid)
                    .admissionPrice(admissionPrice)
                    .pricePerMessage(pricePerMessage)
                    .recordingEnabled(recordingEnabled)
                    .build();
            creatorGoLiveService.goLive(c.getId(), request);
        });
        
        log.info("STREAM: Creator {} prepared stream {} with title: '{}'. Waiting for OBS.", creator.getEmail(), saved.getId(), title);
        
        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.VISIT, // Placeholder for stream event
                creator,
                Map.of("roomId", saved.getId(), "action", "STREAM_PREPARE", "title", title)
        );
        
        return saved;
    }

    private void broadcastSystemMessage(UUID roomId, String content) {
        StreamRoom room = getRoom(roomId);
        Long creatorId = room.getCreatorId();

        ChatMessage systemMessage = ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .content(content)
                .system(true)
                .timestamp(Instant.now())
                .build();
        
        // Use RealtimeMessage for consistency if expected by frontend (Using creatorId routing)
        RealtimeMessage realtimeMessage = RealtimeMessage.ofChat(systemMessage);
        
        messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, realtimeMessage);
    }

    @Transactional
    public StreamRoom stopStream(User user) {
        return stopStream(user, "creator");
    }

    @Transactional
    public StreamRoom stopStream(User user, String reason) {
        Long userId = user.getId();
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Creator not found: " + userId));

        // Update legacy StreamRoom
        StreamRoom room = getCreatorRoom(creator);
        room.setLive(false);
        room.setEndedAt(Instant.now());
        room.setViewerCount(0);
        StreamRoom saved = room;

        // Update unified Stream entity
        List<Stream> liveStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(userId);
        if (!liveStreams.isEmpty()) {
            Stream stream = liveStreams.get(0);
            stream.setLive(false);
            stream.setEndedAt(Instant.now());
            streamRepository.save(stream);
            log.info("STREAM: Updated unified Stream entity {} to isLive=false", stream.getId());
            // Clean up UUID-based Redis viewer keys (new format)
            liveViewerCounterService.resetViewerCountByStreamId(stream.getId(), userId);
        }
        
        // Also stop the core LiveStream (which handles notifications, chat deletion, and Redis cleanup)
        liveStreamService.stopStream(userId, reason);

        // Clear pay-to-watch access list
        tokenService.clearAccess(userId);

        // Clean up assistant bot in-memory state to prevent memory leaks
        streamAssistantBotService.onStreamEnded(userId);

        log.info("STREAM: Creator {} stopped stream", creator.getEmail());
        
        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.VISIT, // Placeholder
                creator,
                Map.of("action", "STREAM_STOP")
        );
        
        return saved;
    }

    @Transactional
    public void updateViewerCount(UUID roomId, int delta) {
        // No-op as stream_rooms table is gone and we use Redis for real-time counts
        log.info("STREAM_LEGACY_WRITE_DEPRECATED: updateViewerCount called for roomId={}", roomId);
    }

    @Transactional(readOnly = true)
    public List<StreamRoom> getActiveRooms() {
        return getLiveStreams();
    }

    public long getActiveStreamCount() {
        return streamRepository.countByIsLiveTrue();
    }

    @Transactional
    public void closeRoom(UUID roomId) {
        StreamRoom room = getRoom(roomId);
        if (room.isLive()) {
            stopStream(room.getCreator());
            log.info("STREAM: Room {} closed by administrator", roomId);
        }
    }

    @Transactional
    public void setSlowMode(UUID roomId, boolean enabled) {
        log.info("STREAM: Setting slowMode to {} for roomId={}", enabled, roomId);
        
        // Update unified Stream
        streamRepository.findById(roomId).ifPresent(stream -> {
            stream.setSlowMode(enabled);
            streamRepository.save(stream);
        });

        String status = enabled ? "enabled" : "disabled";
        broadcastSystemMessage(roomId, "Slow mode has been " + status + " by an administrator.");
        log.info("STREAM: Slow mode {} for room {}", status, roomId);
    }

    @EventListener
    public void handleStreamStartedV2(StreamStartedEventV2 event) {
        log.info("Handling StreamStartedEventV2 for stream {} creator {}", event.getStreamId(), event.getCreatorUserId());
        Long creatorUserId = event.getCreatorUserId();
        int viewerCount = (int) liveViewerCounterService.getViewerCount(creatorUserId);
        int fraudRiskScore = fraudRiskScoreService.getLatestScore(creatorUserId);
        adminRealtimeEventService.broadcastStreamStarted(event, viewerCount, fraudRiskScore);

        // Broadcast public stream status for homepage real-time updates
        broadcastPublicStreamStatusV2(creatorUserId, event.getStreamId(), true, viewerCount);
    }

    @EventListener
    public void handleStreamEndedV2(StreamEndedEventV2 event) {
        log.info("Handling StreamEndedEventV2 for stream {} creator {} reason={}", event.getStreamId(), event.getCreatorUserId(), event.getReason());
        Long creatorUserId = event.getCreatorUserId();
        int viewerCount = (int) liveViewerCounterService.getViewerCount(creatorUserId);
        adminRealtimeEventService.broadcastStreamStopped(event, viewerCount);

        // Broadcast public stream status for homepage real-time updates
        broadcastPublicStreamStatusV2(creatorUserId, event.getStreamId(), false, 0);
    }

    private void broadcastPublicStreamStatusV2(Long creatorUserId, java.util.UUID streamId, boolean isLive, int viewerCount) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("creatorUserId", creatorUserId);
            payload.put("streamId", streamId.toString());
            payload.put("isLive", isLive);
            payload.put("viewerCount", viewerCount);

            String eventType = isLive ? "STREAM_STARTED" : "STREAM_ENDED";
            RealtimeMessage message = RealtimeMessage.of(eventType, payload);
            messagingTemplate.convertAndSend("/exchange/amq.topic/streams.status", message);

            log.debug("STREAM: Public status V2 broadcast: {} for creator {}", eventType, creatorUserId);
        } catch (Exception e) {
            log.error("STREAM: Failed to broadcast public stream status V2: {}", e.getMessage());
        }
    }

}
