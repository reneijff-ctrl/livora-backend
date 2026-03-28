package com.joinlivora.backend.websocket;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.UserStatus;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class WebSocketInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final StreamRepository streamRepository;
    private final LiveAccessService liveAccessService;

    public WebSocketInterceptor(
            JwtService jwtService,
            StreamRepository streamRepository,
            LiveAccessService liveAccessService) {
        this.jwtService = jwtService;
        this.streamRepository = streamRepository;
        this.liveAccessService = liveAccessService;
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

        // --- Presence & Chat (Public) ---
        if ("/exchange/amq.topic/presence".equals(destination) || "/exchange/amq.topic/creators.presence".equals(destination) || destination.startsWith("/exchange/amq.topic/chat.")) {
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
        // Simplified validation to break circular dependency.
        // Business logic validation moved to specific signaling services.
        if (getAuthenticatedUser(accessor) == null) {
            throw new AccessDeniedException("Authentication required to access stream video");
        }
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
