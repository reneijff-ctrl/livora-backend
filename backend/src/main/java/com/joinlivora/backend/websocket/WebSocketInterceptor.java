package com.joinlivora.backend.websocket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.chat.ChatMode;
import com.joinlivora.backend.chat.PPVChatAccessService;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.config.MetricsService;
import com.joinlivora.backend.streaming.service.LivestreamAccessService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.UserStatus;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class WebSocketInterceptor implements ChannelInterceptor {

    /** Cache key prefix for ChatRoom objects: {@code chat:cache:room:{creatorId}} */
    private static final String CHAT_ROOM_CACHE_PREFIX = "chat:cache:room:";
    private static final Duration CHAT_ROOM_CACHE_TTL = Duration.ofMinutes(5);

    private final JwtService jwtService;
    private final StreamRepository streamRepository;
    private final LiveAccessService liveAccessService;
    private final LivestreamAccessService livestreamAccessService;
    private final ChatRoomRepository chatRoomRepository;
    private final PPVChatAccessService ppvChatAccessService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    public WebSocketInterceptor(
            JwtService jwtService,
            StreamRepository streamRepository,
            LiveAccessService liveAccessService,
            LivestreamAccessService livestreamAccessService,
            @Qualifier("chatRoomRepositoryV2") ChatRoomRepository chatRoomRepository,
            PPVChatAccessService ppvChatAccessService,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            MetricsService metricsService) {
        this.jwtService = jwtService;
        this.streamRepository = streamRepository;
        this.liveAccessService = liveAccessService;
        this.livestreamAccessService = livestreamAccessService;
        this.chatRoomRepository = chatRoomRepository;
        this.ppvChatAccessService = ppvChatAccessService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // Ensure accessor is mutable
        if (!accessor.isMutable()) {
            accessor = StompHeaderAccessor.wrap(message);
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.SUBSCRIBE.equals(command) || StompCommand.SEND.equals(command) || StompCommand.CONNECT.equals(command)) {
            validateUserNotSuspended(accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            handleSend(accessor);
        }

        return org.springframework.messaging.support.MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    private void validateUserNotSuspended(StompHeaderAccessor accessor) {
        WebSocketUserInfo user = getAuthenticatedUser(accessor);
        if (user != null) {
            if (UserStatus.SUSPENDED.name().equals(user.status()) || 
                UserStatus.TERMINATED.name().equals(user.status())) {
                log.warn("SECURITY: WebSocket action blocked for RESTRICTED user: {}", user.email());
                throw new AccessDeniedException("User suspended");
            }
        }
    }

    private WebSocketUserInfo getAuthenticatedUser(StompHeaderAccessor accessor) {
        if (accessor.getSessionAttributes() != null) {
            Object cached = accessor.getSessionAttributes().get("ws_user");
            if (cached instanceof WebSocketUserInfo) {
                return (WebSocketUserInfo) cached;
            }
        }
        return null;
    }

    private void handleSend(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            log.warn("WebSocket: Anonymous SEND attempt to {} rejected", accessor.getDestination());
            throw new AccessDeniedException("Authentication required to send messages");
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            log.warn("WebSocket: Anonymous SUBSCRIBE attempt to {} rejected", accessor.getDestination());
            throw new AccessDeniedException("Authentication required to subscribe to topics");
        }
        String destination = accessor.getDestination();
        Principal principal = accessor.getUser();

        if (destination == null) {
            throw new AccessDeniedException("Destination required");
        }

        // --- Admin Access ---
        if (destination.startsWith("/exchange/amq.topic/admin.")) {
            if (principal instanceof UsernamePasswordAuthenticationToken auth) {
                boolean isAdmin = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                if (!isAdmin) {
                    throw new AccessDeniedException("Admin topic access denied");
                }
            } else {
                throw new AccessDeniedException("Admin topic access denied");
            }
            return;
        }

        // --- Creator Earnings Access Validation ---
        if (destination.startsWith("/exchange/amq.topic/creator.")) {
            validateCreatorEarningsAccess(destination, accessor);
            return;
        }

        // --- Tips (Public) ---
        if (destination.startsWith("/exchange/amq.topic/rooms.") && destination.endsWith(".tips")) {
            return;
        }

        // --- Presence (Public) ---
        if ("/exchange/amq.topic/presence".equals(destination) || "/exchange/amq.topic/creators.presence".equals(destination)) {
            return;
        }

        // --- Chat (access-controlled) ---
        if (destination.startsWith("/exchange/amq.topic/chat.")) {
            validateChatSubscription(destination, accessor);
            return;
        }

        // --- Livestream Monetization, Goals, Leaderboard & System (Public) ---
        if (destination.startsWith("/exchange/amq.topic/monetization.")) {
            return;
        }
        if (destination.startsWith("/exchange/amq.topic/goals.")) {
            return;
        }
        if (destination.startsWith("/exchange/amq.topic/leaderboard.")) {
            return;
        }
        if (destination.startsWith("/exchange/amq.topic/system.")) {
            return;
        }

        // --- Stream Status (Public) ---
        if (destination.startsWith("/exchange/amq.topic/stream.v2.creator.")) {
            return;
        }
        if ("/exchange/amq.topic/streams.status".equals(destination)) {
            return;
        }

        // --- Premium ---
        if ("/exchange/amq.topic/premium".equals(destination)) {
            validatePremiumAccess(principal, "PREMIUM topic");
            return;
        } else if (destination.startsWith("/exchange/amq.topic/streams.premium.")) {
            validatePremiumAccess(principal, "PREMIUM stream");
            return;
        }

        // --- Stream Video (UUID) ---
        if (destination.startsWith("/exchange/amq.topic/stream.") && destination.endsWith(".video")) {
            String streamIdStr = destination.substring("/exchange/amq.topic/stream.".length(), destination.length() - ".video".length());
            validateStreamVideoAccess(streamIdStr, accessor);
            return;
        }

        // --- Viewers (Numeric Creator ID) ---
        if (destination.startsWith("/exchange/amq.topic/viewers.")) {
            String creatorIdStr = destination.substring("/exchange/amq.topic/viewers.".length());
            WebSocketUserInfo user = getAuthenticatedUser(accessor);
            try {
                Long creatorId = Long.parseLong(creatorIdStr);
                validateViewerCountAccess(user != null ? user.id() : null, creatorId);
            } catch (NumberFormatException e) {
                // If ID is not a Long (e.g., UUID), it's still public, so we allow it
            }
            return;
        }

        // --- WebRTC signaling ---
        if (destination.startsWith("/exchange/amq.topic/webrtc.room.")) {
            validateWebRtcRoomAccess(destination, accessor);
            return;
        }

        // --- Private Queues ---
        if (destination.startsWith("/user/queue/") || destination.startsWith("/queue/")) {
            return;
        }

        // --- FAIL CLOSED ---
        log.warn("SECURITY: Unauthorized WebSocket topic attempt: {}", destination);
        throw new AccessDeniedException("Unauthorized WebSocket topic");
    }

    private void validateStreamVideoAccess(String streamIdStr, StompHeaderAccessor accessor) {
        WebSocketUserInfo user = getAuthenticatedUser(accessor);
        if (user == null) {
            log.warn("WebSocket subscription denied: unauthenticated attempt to subscribe to stream video topic {}", streamIdStr);
            throw new AccessDeniedException("Authentication required to access stream video");
        }

        UUID streamId;
        try {
            streamId = UUID.fromString(streamIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("WebSocket subscription denied: invalid stream UUID '{}' for user {}", streamIdStr, user.email());
            throw new AccessDeniedException("Invalid stream ID");
        }

        Optional<Stream> streamOpt = streamRepository.findById(streamId);
        if (streamOpt.isEmpty()) {
            log.warn("WebSocket subscription denied: stream {} not found for user {}", streamId, user.email());
            throw new AccessDeniedException("Stream not found");
        }

        Stream stream = streamOpt.get();

        // Admins and moderators always allowed
        if (Role.ADMIN.name().equals(user.role()) || Role.MODERATOR.name().equals(user.role())) {
            return;
        }

        // Creator of the stream is always allowed
        if (stream.getCreator() != null && stream.getCreator().getId().equals(user.id())) {
            return;
        }

        // Stream must be live for viewers
        if (!stream.isLive()) {
            log.warn("WebSocket subscription denied: stream {} is not live, user {}", streamId, user.email());
            throw new AccessDeniedException("Stream is not live");
        }

        // For paid streams, verify the viewer has purchased admission (UUID-based check only)
        if (stream.isPaid()) {
            if (!livestreamAccessService.hasAccess(streamId, user.id())) {
                log.warn("WebSocket subscription denied: paid stream {} requires purchased access, user {}", streamId, user.email());
                throw new AccessDeniedException("Paid stream access required");
            }
        }
    }

    private void validateChatSubscription(String destination, StompHeaderAccessor accessor) {
        WebSocketUserInfo user = getAuthenticatedUser(accessor);
        if (user == null) {
            log.warn("WebSocket subscription denied: unauthenticated attempt to subscribe to chat topic {}", destination);
            throw new AccessDeniedException("Authentication required to subscribe to chat");
        }

        // destination format: /exchange/amq.topic/chat.{creatorUserId}
        String creatorIdStr = destination.substring("/exchange/amq.topic/chat.".length());
        Long creatorId;
        try {
            creatorId = Long.parseLong(creatorIdStr);
        } catch (NumberFormatException e) {
            log.warn("WebSocket subscription denied: invalid chat topic format '{}', user {}", destination, user.email());
            throw new AccessDeniedException("Invalid chat topic");
        }

        Optional<ChatRoom> roomOpt = getChatRoomCached(creatorId);
        if (roomOpt.isEmpty()) {
            // No room yet — allow subscription (room may not have been created yet)
            log.debug("Chat subscription: no ChatRoom found for creatorId={}, allowing subscription for user {}", creatorId, user.email());
            return;
        }

        ChatRoom room = roomOpt.get();
        ChatMode chatMode = room.getChatMode();

        // Admins and moderators bypass all chat access restrictions
        if (Role.ADMIN.name().equals(user.role()) || Role.MODERATOR.name().equals(user.role())) {
            return;
        }

        // Creator of the room is always allowed
        if (creatorId.equals(user.id())) {
            return;
        }

        // PUBLIC rooms: allow everyone authenticated
        if (chatMode == ChatMode.PUBLIC && !room.isPaid() && !room.isPrivate()) {
            return;
        }

        // PRIVATE rooms: only the creator (already handled above) and the assigned viewer
        if (room.isPrivate()) {
            if (room.getViewerId() != null && room.getViewerId().equals(user.id())) {
                return;
            }
            log.warn("WebSocket subscription denied: private chat room for creator {}, user {}", creatorId, user.email());
            throw new AccessDeniedException("Private chat room access denied");
        }

        // SUBSCRIBERS_ONLY: requires ROLE_PREMIUM (subscriber)
        if (chatMode == ChatMode.SUBSCRIBERS_ONLY) {
            if (!hasAuthority(accessor, "ROLE_PREMIUM")) {
                log.warn("WebSocket subscription denied: subscribers-only chat for creator {}, user {}", creatorId, user.email());
                throw new AccessDeniedException("Subscriber access required for this chat room");
            }
            return;
        }

        // CREATORS_ONLY: requires ROLE_CREATOR or higher
        if (chatMode == ChatMode.CREATORS_ONLY) {
            if (!hasAuthority(accessor, "ROLE_CREATOR") && !hasAuthority(accessor, "ROLE_ADMIN")) {
                log.warn("WebSocket subscription denied: creators-only chat for creator {}, user {}", creatorId, user.email());
                throw new AccessDeniedException("Creator access required for this chat room");
            }
            return;
        }

        // MODERATORS_ONLY: requires ROLE_MODERATOR or ROLE_ADMIN
        if (chatMode == ChatMode.MODERATORS_ONLY) {
            log.warn("WebSocket subscription denied: moderators-only chat for creator {}, user {}", creatorId, user.email());
            throw new AccessDeniedException("Moderator access required for this chat room");
        }

        // PPV-paid room: verify PPV access using the creator's live stream UUID
        if (room.isPaid()) {
            var liveStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorId);
            if (liveStreams.isEmpty()) {
                log.warn("WebSocket subscription denied: PPV chat for creator {} — no live stream found, user {}", creatorId, user.email());
                throw new AccessDeniedException("No live stream found for PPV chat room");
            }
            UUID streamId = liveStreams.get(0).getId();
            if (!ppvChatAccessService.hasAccess(user.id(), streamId)) {
                log.warn("WebSocket subscription denied: PPV chat for creator {}, stream {}, user {}", creatorId, streamId, user.email());
                throw new AccessDeniedException("PPV access required for this chat room");
            }
        }
    }

    /**
     * Look up a ChatRoom by creatorId with a 5-minute Redis cache.
     * On Redis failure, falls back to a direct DB query (fail-open).
     */
    private Optional<ChatRoom> getChatRoomCached(Long creatorId) {
        String cacheKey = CHAT_ROOM_CACHE_PREFIX + creatorId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                ChatRoom room = objectMapper.readValue(cached, ChatRoom.class);
                metricsService.getCacheCharoomHit().increment();
                log.debug("ChatRoom cache HIT for creatorId={}", creatorId);
                return Optional.of(room);
            }
        } catch (Exception e) {
            log.debug("ChatRoom cache read failed for creatorId={}, falling back to DB: {}", creatorId, e.getMessage());
        }

        // Cache miss — fetch from DB
        metricsService.getCacheCharoomMiss().increment();
        log.debug("ChatRoom cache MISS for creatorId={}, querying DB", creatorId);
        Optional<ChatRoom> roomOpt = chatRoomRepository.findByCreatorId(creatorId);

        // Populate cache on hit
        roomOpt.ifPresent(room -> {
            try {
                String json = objectMapper.writeValueAsString(room);
                stringRedisTemplate.opsForValue().set(cacheKey, json, CHAT_ROOM_CACHE_TTL);
            } catch (Exception e) {
                log.debug("ChatRoom cache write failed for creatorId={}: {}", creatorId, e.getMessage());
            }
        });
        return roomOpt;
    }

    private boolean hasAuthority(StompHeaderAccessor accessor, String authority) {
        Principal principal = accessor.getUser();
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals(authority));
        }
        return false;
    }

    private void validateWebRtcRoomAccess(String destination, StompHeaderAccessor accessor) {
        WebSocketUserInfo user = getAuthenticatedUser(accessor);
        if (user == null) {
            throw new AccessDeniedException("Authentication required to access WebRTC room");
        }

        String roomIdStr = destination.substring(destination.lastIndexOf('.') + 1);
        UUID roomId;
        try {
            roomId = UUID.fromString(roomIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("SECURITY: Invalid WebRTC room ID format: {}", roomIdStr);
            throw new AccessDeniedException("Invalid WebRTC room ID");
        }

        Optional<Stream> streamOpt = streamRepository.findByMediasoupRoomIdWithCreator(roomId);
        if (streamOpt.isEmpty()) {
            log.debug("WebRTC room subscription denied - no stream found for roomId: {} (may occur during stream teardown)", roomId);
            throw new AccessDeniedException("Unauthorized WebRTC room subscription");
        }

        Stream stream = streamOpt.get();

        // Allow if user is the creator
        if (stream.getCreator().getId().equals(user.id())) {
            return;
        }

        // Allow admins and moderators
        if (Role.ADMIN.name().equals(user.role()) || Role.MODERATOR.name().equals(user.role())) {
            return;
        }

        // Stream must be active
        if (!stream.isLive()) {
            log.debug("WebRTC room subscription denied - stream not live for roomId: {} (normal during stream start/stop race)", roomId);
            throw new AccessDeniedException("Unauthorized WebRTC room subscription");
        }

        // For paid streams, verify the user has purchased access
        if (stream.isPaid()) {
            if (!liveAccessService.hasAccess(stream.getCreator().getId(), user.id())) {
                log.warn("SECURITY: WebRTC room subscription denied - paid access required for roomId: {}, user: {}", roomId, user.email());
                throw new AccessDeniedException("Unauthorized WebRTC room subscription");
            }
        }
    }

    private void validateViewerCountAccess(Long userId, Long creatorId) {
        // viewer count is public stream metadata
        // allow all authenticated users to subscribe
        return;
    }


    private void validatePremiumAccess(Principal principal, String resourceName) {
        if (principal == null) {
            throw new AccessDeniedException("Unauthorized");
        }

        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            boolean hasPremium = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_PREMIUM") || 
                                   a.getAuthority().equals("ROLE_ADMIN") || 
                                   a.getAuthority().equals("ROLE_MODERATOR"));

            if (!hasPremium) {
                log.warn("SECURITY: User {} attempted to subscribe to {} without access", principal.getName(), resourceName);
                throw new AccessDeniedException("Premium access required");
            }
        } else {
            throw new AccessDeniedException("Unauthorized");
        }
    }

    private void validateCreatorEarningsAccess(String destination, StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            log.warn("SECURITY: Access denied to creator earnings: Principal is null");
            throw new AccessDeniedException("Authentication required");
        }

        // Expected format: /exchange/amq.topic/creator.{creator}.earnings
        String routingKey = destination.substring("/exchange/amq.topic/".length());
        String[] keyParts = routingKey.split("\\.");
        if (keyParts.length < 3 || !"earnings".equals(keyParts[keyParts.length - 1])) {
            log.warn("SECURITY: Invalid creator earnings destination format: {}", destination);
            throw new AccessDeniedException("Invalid destination");
        }

        String creatorIdStr = keyParts[1];
        try {
            Long creatorId = Long.parseLong(creatorIdStr);
            
            if (principal instanceof UsernamePasswordAuthenticationToken auth) {
                WebSocketUserInfo user = getAuthenticatedUser(accessor);
                Long currentUserId = null;
                if (user != null) {
                    currentUserId = user.id();
                } else {
                    if (auth.getPrincipal() instanceof StompPrincipal sp) {
                        currentUserId = Long.parseLong(sp.getUserId());
                    } else {
                        currentUserId = Long.parseLong(auth.getName());
                    }
                }

                if (currentUserId == null) {
                    throw new AccessDeniedException("User not found");
                }

                // Only the creator themselves or an admin can subscribe
                boolean isAdmin = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                
                if (!currentUserId.equals(creatorId) && !isAdmin) {
                    log.warn("SECURITY: Access denied for user {} to earnings of creator {}", currentUserId, creatorId);
                    throw new AccessDeniedException("Access denied to earnings");
                }
                
                log.info("AUDIT: User {} subscribed to earnings topic for creator ID {}", currentUserId, creatorId);
            } else {
                log.warn("SECURITY: Access denied to creator earnings: Unknown principal type {}", principal.getClass().getName());
                throw new AccessDeniedException("Access denied");
            }
        } catch (NumberFormatException e) {
            log.error("Invalid creator ID format in destination: {}", creatorIdStr);
            throw new AccessDeniedException("Invalid creator ID");
        }
    }
}
