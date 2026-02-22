package com.joinlivora.backend.streaming;

import com.joinlivora.backend.payment.SubscriptionService;
import com.joinlivora.backend.payment.dto.SubscriptionResponse;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Controller
@Slf4j
public class StreamingController {

    private final StreamRoomRepository roomRepository;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final StreamService streamService;
    private final LiveAccessService liveAccessService;

    public StreamingController(
            StreamRoomRepository roomRepository,
            UserService userService,
            SubscriptionService subscriptionService,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            StreamService streamService,
            LiveAccessService liveAccessService) {
        this.roomRepository = roomRepository;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.messagingTemplate = messagingTemplate;
        this.streamService = streamService;
        this.liveAccessService = liveAccessService;
    }

    @MessageMapping("/webrtc/join")
    public void joinRoom(@Payload SignalingMessage message, Principal principal) {
        if (principal == null) return;
        String email = principal.getName();
        User user = userService.getByEmail(email);
        
        StreamRoom room = streamService.getRoom(message.getRoomId());

        // Access Control
        if (room.isPremium()) {
            SubscriptionResponse sub = subscriptionService.getSubscriptionForUser(user);
            boolean hasAccess = user.getRole().name().equals("ADMIN") || 
                               (sub != null && sub.getStatus().name().equals("ACTIVE"));
            
            if (!hasAccess) {
                log.warn("SECURITY: User {} denied access to premium room {}", email, room.getId());
                sendError(email, SignalingMessage.Type.ACCESS_DENIED, "Premium subscription required");
                return;
            }
        }

        // Paid stream access check
        if (room.isPaid()) {
            if (!liveAccessService.hasAccess(room.getCreator().getId(), user.getId())) {
                log.warn("SECURITY: User {} denied access to paid room {}", email, room.getId());
                sendError(email, SignalingMessage.Type.ACCESS_DENIED, "Paid access required");
                return;
            }
        }

        log.info("Viewer incremented for creator {} by viewer {}", room.getCreator().getId(), user.getId());
        log.info("User {} joined room {}", email, room.getId());
        streamService.updateViewerCount(room.getId(), 1);
        
        // Broadcast presence update
        messagingTemplate.convertAndSend("/topic/stream/" + room.getId() + "/presence", 
            Map.of("onlineCount", room.getViewerCount() + 1));
    }

    @MessageMapping("/webrtc/leave")
    public void leaveRoom(@Payload SignalingMessage message, Principal principal) {
        if (message.getRoomId() != null) {
            streamService.updateViewerCount(message.getRoomId(), -1);
        }
    }

    @MessageMapping("/webrtc/offer")
    public void handleOffer(@Payload SignalingMessage message, Principal principal) {
        // Broadcaster sends offer to SFU (or viewer if P2P, but we aim for SFU)
        // For now, we route messages between peers as a basic signaling server
        log.info("WebRTC Offer from {} to {}", principal.getName(), message.getReceiverId());
        forwardSignaling(message, principal.getName());
    }

    @MessageMapping("/webrtc/answer")
    public void handleAnswer(@Payload SignalingMessage message, Principal principal) {
        log.info("WebRTC Answer from {} to {}", principal.getName(), message.getReceiverId());
        forwardSignaling(message, principal.getName());
    }

    @MessageMapping("/webrtc/ice")
    public void handleIceCandidate(@Payload SignalingMessage message, Principal principal) {
        forwardSignaling(message, principal.getName());
    }

    @MessageMapping("/webrtc/start")
    public void startStream(@Payload SignalingMessage message, Principal principal) {
        String email = principal.getName();
        StreamRoom room = roomRepository.findByCreatorEmail(email)
                .orElseThrow(() -> new RuntimeException("Room not found for creatorUserId"));

        // room.setLive(true); // Removed as per instructions
        room.setStartedAt(Instant.now());
        roomRepository.save(room);

        log.info("Stream started by {}", email);
        messagingTemplate.convertAndSend("/topic/streams", 
            SignalingMessage.builder().type(SignalingMessage.Type.STREAM_START).roomId(room.getId()).build());
    }

    @MessageMapping("/webrtc/stop")
    public void stopStream(@Payload SignalingMessage message, Principal principal) {
        String email = principal.getName();
        User creator = userService.getByEmail(email);
        streamService.stopStream(creator);
        log.info("Stream stopped by {}", email);
    }

    @MessageMapping("/admin/stream/stop")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public void adminStopStream(@Payload SignalingMessage message, Principal principal) {
        streamService.closeRoom(message.getRoomId());
        log.info("ADMIN: Stream room {} force-stopped by {}", message.getRoomId(), principal.getName());
    }

    private void forwardSignaling(SignalingMessage message, String senderEmail) {
        message.setSenderId(senderEmail);
        if (message.getReceiverId() != null) {
            messagingTemplate.convertAndSendToUser(message.getReceiverId(), "/queue/webrtc", message);
        }
    }

    private void sendError(String userEmail, SignalingMessage.Type type, String error) {
        messagingTemplate.convertAndSendToUser(userEmail, "/queue/webrtc", 
            SignalingMessage.builder().type(type).message(error).build());
    }
}
