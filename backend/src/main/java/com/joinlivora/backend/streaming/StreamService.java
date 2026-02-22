package com.joinlivora.backend.streaming;

import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.streaming.dto.GoLiveRequest;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.websocket.ChatMessage;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class StreamService {

    private final StreamRoomRepository streamRoomRepository;
    private final LiveStreamService liveStreamService;
    private final ChatRoomService chatRoomService;
    private final UserService userService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final CreatorGoLiveService creatorGoLiveService;
    private final com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository;
    private final com.joinlivora.backend.token.TokenService tokenService;
    private final LiveViewerCounterService liveViewerCounterService;
    private final CreatorVerificationRepository creatorVerificationRepository;

    public StreamService(
            StreamRoomRepository streamRoomRepository,
            LiveStreamService liveStreamService,
            ChatRoomService chatRoomService,
            UserService userService,
            AnalyticsEventPublisher analyticsEventPublisher,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            CreatorGoLiveService creatorGoLiveService,
            com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository,
            com.joinlivora.backend.token.TokenService tokenService,
            LiveViewerCounterService liveViewerCounterService,
            CreatorVerificationRepository creatorVerificationRepository) {
        this.streamRoomRepository = streamRoomRepository;
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
    }

    public List<StreamRoom> getLiveStreams() {
        return streamRoomRepository.findAllByIsLiveTrue();
    }

    public StreamRoom getRoom(UUID id) {
        return streamRoomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stream room not found"));
    }

    public java.util.Optional<StreamRoom> findByCreatorEmail(String email) {
        return streamRoomRepository.findByCreatorEmail(email);
    }

    public StreamRoom getCreatorRoom(User creator) {
        return streamRoomRepository.findByCreator(creator)
                .orElseGet(() -> streamRoomRepository.save(
                        StreamRoom.builder()
                                .creator(creator)
                                .isLive(false)
                                .viewerCount(0)
                                .build()
                ));
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
        
        StreamRoom saved = streamRoomRepository.save(room);
        
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

    private void broadcastSystemMessage(UUID streamId, String content) {
        ChatMessage systemMessage = ChatMessage.builder()
                .content(content)
                .system(true)
                .timestamp(Instant.now())
                .build();
        
        // Use RealtimeMessage for consistency if expected by frontend
        RealtimeMessage realtimeMessage = RealtimeMessage.ofChat(systemMessage);
        
        messagingTemplate.convertAndSend("/topic/chat/" + streamId, realtimeMessage);
    }

    @Transactional
    public StreamRoom stopStream(User creator) {
        StreamRoom room = getCreatorRoom(creator);
        // room.setLive(false); // Removed as per instructions
        room.setEndedAt(Instant.now());
        room.setViewerCount(0);
        
        StreamRoom saved = streamRoomRepository.save(room);
        
        // Also stop the core LiveStream (which handles notifications and chat deletion)
        liveStreamService.stopStream(creator.getId());

        // Clear pay-to-watch access list
        tokenService.clearAccess(creator.getId());

        // Reset viewer count in Redis
        liveViewerCounterService.resetViewerCount(creator.getId());

        log.info("STREAM: Creator {} stopped stream {}", creator.getEmail(), saved.getId());
        
        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.VISIT, // Placeholder
                creator,
                Map.of("roomId", saved.getId(), "action", "STREAM_STOP")
        );
        
        return saved;
    }

    @Transactional
    public void updateViewerCount(UUID roomId, int delta) {
        // Update viewer count via entity load/save to ensure downstream listeners/tests observe the change
        streamRoomRepository.findById(roomId).ifPresent(room -> {
            int current = room.getViewerCount();
            int next = current + delta;
            room.setViewerCount(Math.max(0, next));
            streamRoomRepository.save(room);
        });
    }

    @Transactional(readOnly = true)
    public List<StreamRoom> getActiveRooms() {
        return streamRoomRepository.findAllByIsLiveTrue();
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
        StreamRoom room = getRoom(roomId);
        room.setSlowMode(enabled);
        streamRoomRepository.save(room);
        
        String status = enabled ? "enabled" : "disabled";
        broadcastSystemMessage(roomId, "Slow mode has been " + status + " by an administrator.");
        log.info("STREAM: Slow mode {} for room {}", status, roomId);
    }
}
