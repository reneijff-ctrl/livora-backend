package com.joinlivora.backend.streaming;

import com.joinlivora.backend.payment.SubscriptionService;
import com.joinlivora.backend.payment.dto.SubscriptionResponse;
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
@RequiredArgsConstructor
@Slf4j
public class StreamingController {

    private final StreamRoomRepository roomRepository;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final StreamService streamService;

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
                .orElseThrow(() -> new RuntimeException("Room not found for creator"));

        room.setLive(true);
        room.setStartedAt(Instant.now());
        roomRepository.save(room);

        log.info("Stream started by {}", email);
        messagingTemplate.convertAndSend("/topic/streams", 
            SignalingMessage.builder().type(SignalingMessage.Type.STREAM_START).roomId(room.getId()).build());
    }

    @MessageMapping("/webrtc/stop")
    public void stopStream(@Payload SignalingMessage message, Principal principal) {
        String email = principal.getName();
        StreamRoom room = roomRepository.findByCreatorEmail(email)
                .orElseThrow(() -> new RuntimeException("Room not found for creator"));

        room.setLive(false);
        roomRepository.save(room);

        log.info("Stream stopped by {}", email);
        messagingTemplate.convertAndSend("/topic/streams", 
            SignalingMessage.builder().type(SignalingMessage.Type.STREAM_STOP).roomId(room.getId()).build());
    }

    @MessageMapping("/admin/stream/stop")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public void adminStopStream(@Payload SignalingMessage message, Principal principal) {
        StreamRoom room = roomRepository.findById(message.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        room.setLive(false);
        roomRepository.save(room);

        log.info("ADMIN: Stream room {} force-stopped by {}", room.getId(), principal.getName());
        
        // Notify creator
        messagingTemplate.convertAndSendToUser(room.getCreator().getEmail(), "/queue/webrtc", 
            SignalingMessage.builder().type(SignalingMessage.Type.ERROR).message("Your stream was stopped by an administrator.").build());
        
        // Notify everyone
        messagingTemplate.convertAndSend("/topic/streams", 
            SignalingMessage.builder().type(SignalingMessage.Type.STREAM_STOP).roomId(room.getId()).build());
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
