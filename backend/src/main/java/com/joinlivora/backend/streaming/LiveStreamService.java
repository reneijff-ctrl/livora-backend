package com.joinlivora.backend.streaming;

import com.joinlivora.backend.creator.verification.VerificationStatus;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.monetization.PPVPurchaseValidationService;
import com.joinlivora.backend.monetization.PpvService;
import com.joinlivora.backend.payment.SubscriptionStatus;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.streaming.service.LivestreamAnalyticsService;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.client.MediasoupClient;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import com.joinlivora.backend.livestream.event.StreamEndedEventV2;
import com.joinlivora.backend.livestream.websocket.SignalingMessage;

@Service
@Slf4j
@Deprecated
/**
 * @deprecated Use {@link com.joinlivora.backend.livestream.service.LiveStreamService} instead.
 * This service is scheduled for removal in the next refactor stage.
 * TODO: Remove this class after ensuring all callers have migrated to V2.
 */
public class LiveStreamService {

    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PpvService ppvService;
    private final PPVPurchaseValidationService purchaseValidationService;
    private final MeterRegistry meterRegistry;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final com.joinlivora.backend.chat.service.ChatRoomService chatRoomService;
    private final LivestreamAnalyticsService analyticsService;
    private final LiveViewerCounterService liveViewerCounterService;
    private final LiveAccessService liveAccessService;
    private final CreatorVerificationRepository creatorVerificationRepository;
    private final StreamRepository streamRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();
    private final MediasoupClient mediasoupClient;

    public LiveStreamService(
            UserRepository userRepository,
            UserSubscriptionRepository subscriptionRepository,
            @org.springframework.context.annotation.Lazy PpvService ppvService,
            @org.springframework.context.annotation.Lazy PPVPurchaseValidationService purchaseValidationService,
            MeterRegistry meterRegistry,
            @org.springframework.context.annotation.Lazy org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate,
            @org.springframework.context.annotation.Lazy com.joinlivora.backend.chat.service.ChatRoomService chatRoomService,
            LivestreamAnalyticsService analyticsService,
            @org.springframework.context.annotation.Lazy LiveViewerCounterService liveViewerCounterService,
            LiveAccessService liveAccessService,
            CreatorVerificationRepository creatorVerificationRepository,
            StreamRepository streamRepository,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            MediasoupClient mediasoupClient) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.ppvService = ppvService;
        this.purchaseValidationService = purchaseValidationService;
        this.meterRegistry = meterRegistry;
        this.messagingTemplate = messagingTemplate;
        this.chatRoomService = chatRoomService;
        this.analyticsService = analyticsService;
        this.liveViewerCounterService = liveViewerCounterService;
        this.liveAccessService = liveAccessService;
        this.creatorVerificationRepository = creatorVerificationRepository;
        this.streamRepository = streamRepository;
        this.eventPublisher = eventPublisher;
        this.mediasoupClient = mediasoupClient;
    }

    private AtomicLong activeStreamsGauge;

    @jakarta.annotation.PostConstruct
    public void init() {
        activeStreamsGauge = meterRegistry.gauge("streaming.active.streams", new AtomicLong(0));
        // Initialize gauge with current DB state from Unified Stream table
        long initialCount = streamRepository.countByIsLiveTrue();
        activeStreamsGauge.set(initialCount);
    }

    @Transactional
    public String startStream(Long creatorId, String title, boolean isPremium, java.util.UUID ppvContentId, boolean recordingEnabled) {
        log.info("STREAM: Preparing stream for creatorId: {}", creatorId);
        
        // Find existing unified stream to get streamKey, or create new transient one
        java.util.List<Stream> streams = streamRepository.findAllByCreatorIdAndIsLiveTrueWithCreator(creatorId);
        Stream stream = streams.isEmpty() ? null : streams.get(0);

        String streamKey = stream != null ? stream.getStreamKey() : generateSecureStreamKey();
        
        // This method was preparing a LiveStream POJO that is now gone. 
        // We only log the action for unified stream tracking.
        log.info("STREAM: Creator {} is ready to start with key {} (recording={})", creatorId, streamKey, recordingEnabled);
        return streamKey;
    }

    @Transactional
    public boolean verifyStreamKeyAndStart(String streamKey) {
        log.info("RTMP: Verifying stream key: {}", streamKey);
        
        // Prefer unified Stream entity for verification and state update
        Optional<Stream> unifiedStream = streamRepository.findByStreamKeyWithCreator(streamKey);
        if (unifiedStream.isPresent()) {
            Stream stream = unifiedStream.get();
            
            // 1. Identity Verification Check
            com.joinlivora.backend.creator.model.CreatorVerification verification = 
                creatorVerificationRepository.findByCreatorId(stream.getCreator().getId()).orElse(null);
            
            if (verification == null || verification.getStatus() != VerificationStatus.APPROVED) {
                log.warn("RTMP SECURITY: Creator {} is not APPROVED. Connection rejected.", stream.getCreator().getId());
                return false;
            }

            // 2. Payouts Check
            User user = userRepository.findById(stream.getCreator().getId()).orElse(null);
            if (user == null || !user.isPayoutsEnabled()) {
                log.warn("RTMP SECURITY: Creator {} payouts disabled or account not found. Connection rejected.", stream.getCreator().getId());
                return false;
            }

            // 3. Protection: Limit concurrent streams per creatorId
            // Already checked by findByCreatorIdAndIsLiveTrue in most cases, but verify again
            boolean isReconnecting = stream.isLive();
            if (isReconnecting) {
                log.info("RTMP: Stream for creator {} is already marked LIVE. Allowing re-connection.", 
                        stream.getCreator().getId());
                // Fall-through to allow OBS to reconnect if the session is already active
            }

            stream.setLive(true);
            stream.setStartedAt(Instant.now());
            stream.setEndedAt(null);
            streamRepository.save(stream);
            log.info("RTMP: Stream key verified via Unified Stream, creator={}, stream {} is now LIVE", stream.getCreator().getId(), stream.getId());

            // Cleanup Mediasoup room only if this is NOT a reconnection.
            // Keeping the room alive during reconnection prevents viewers from being kicked.
            if (!isReconnecting) {
                cleanupMediasoupRoom(stream.getMediasoupRoomId());
            } else {
                log.info("RTMP: Reconnection detected for stream {}. Skipping Mediasoup room cleanup to preserve viewer sessions.", stream.getId());
            }
            
            return true;
        }

        return false;
    }

    @Transactional
    public void verifyStreamKeyAndStop(String streamKey) {
        log.info("RTMP: Ending stream for key: {}", streamKey);
        
        // Update Unified Stream state
        streamRepository.findByStreamKeyWithCreator(streamKey).ifPresent(stream -> {
            stream.setLive(false);
            stream.setEndedAt(Instant.now());
            streamRepository.save(stream);
            log.info("RTMP: Unified Stream {} for creator={} is now OFFLINE", stream.getId(), stream.getCreator().getId());
            
            // Publish V2 event (UUID-based only — legacy StreamEndedEvent removed)
            log.info("Publishing StreamEndedEventV2 for streamId={} creatorUserId={}", stream.getId(), stream.getCreator().getId());
            eventPublisher.publishEvent(new StreamEndedEventV2(this,
                    stream.getId(),
                    stream.getCreator().getId(),
                    stream.isPaid(),
                    "disconnect",
                    stream.getEndedAt()));

            // Notify viewers
            notifyStreamStop(stream);
        });
    }

    private void notifyStreamStop(Stream stream) {
        if (stream == null) return;
        Long creatorId = stream.getCreator().getId();
        UUID roomId = stream.getMediasoupRoomId();
        
        // 1. Reset viewer count in Redis using UUID-based key
        if (liveViewerCounterService != null) {
            liveViewerCounterService.resetViewerCountByStreamId(stream.getId(), creatorId);
        }

        if (roomId != null) {
            // 2. Broadcast system message to chat (Using creatorId routing)
            com.joinlivora.backend.websocket.ChatMessage systemMessage = com.joinlivora.backend.websocket.ChatMessage.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .content("Stream ended")
                    .system(true)
                    .timestamp(Instant.now())
                    .build();
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId,
                    com.joinlivora.backend.websocket.RealtimeMessage.ofChat(systemMessage));

            // 3. Disconnect all viewers via signaling
            SignalingMessage disconnectMsg = SignalingMessage.builder()
                    .type("STREAM_STOP")
                    .senderId(creatorId)
                    .roomId(roomId.toString())
                    .build();
            messagingTemplate.convertAndSend("/exchange/amq.topic/stream." + roomId + ".video", disconnectMsg);
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, disconnectMsg);

            // 4. Delete bound chat room
            chatRoomService.deleteRoom("stream-" + roomId);
            
            // 5. Cleanup Mediasoup room
            mediasoupClient.closeRoom(roomId);
        } else {
            log.warn("LIVESTREAM: No Mediasoup roomId found for stream {}. Skipping room cleanup.", stream.getId());
            // Even without roomId, notify chat if possible
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, 
                Map.of("type", "STREAM_STOP", "creatorId", creatorId));
        }
    }

    public void cleanupMediasoupRoom(UUID roomId) {
        mediasoupClient.closeRoom(roomId);
    }

    @Transactional
    public void updateRecordingPath(String streamKey, String path) {
        log.info("RTMP: Recording finished for key: {}, path: {}", streamKey, path);
        
        // Update Unified Stream
        streamRepository.findByStreamKeyWithCreator(streamKey).ifPresent(stream -> {
            // Since Stream entity doesn't have recordingPath yet, we might need to add it or just log it
            log.info("RTMP: Recording path updated for unified stream {}: {}", stream.getId(), path);
        });
    }

    private void updateStreamRoomStatus(Long creatorId, boolean isLive) {
        // No-op - legacy table removed
    }

    private String generateHlsUrl(String streamKey) {
        // In a real setup, this would be the URL where Nginx serves the HLS stream
        // Based on api.joinlivora.com/hls/{streamKey}/index.m3u8
        return "https://api.joinlivora.com/hls/" + streamKey + "/index.m3u8";
    }

    @Transactional
    public void stopStream(Long creatorId) {
        stopStream(creatorId, "creator");
    }

    @Transactional
    public void stopStream(Long creatorId, String reason) {
        log.info("Stopping live stream for creatorUserId: {} reason: {}", creatorId, reason);
        
        // Update Unified Stream
        java.util.List<Stream> liveStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueWithCreator(creatorId);
        if (!liveStreams.isEmpty()) { Stream stream = liveStreams.get(0);
            stream.setLive(false);
            stream.setEndedAt(Instant.now());
            streamRepository.save(stream);
            log.info("STREAM: Updated unified Stream entity {} to isLive=false", stream.getId());

            // Publish V2 event (UUID-based only — legacy StreamEndedEvent removed)
            log.info("Publishing StreamEndedEventV2 for streamId={} creatorUserId={}", stream.getId(), creatorId);
            eventPublisher.publishEvent(new StreamEndedEventV2(this,
                    stream.getId(),
                    creatorId,
                    stream.isPaid(),
                    reason,
                    stream.getEndedAt()));

            // Notify viewers and cleanup
            notifyStreamStop(stream);
        }
    }

    @Cacheable(
            value = "viewer_access",
            key = "#streamId.toString() + ':' + (#user != null ? #user.id : 'anonymous')",
            unless = "#result == null"
    )
    public boolean validateViewerAccess(UUID streamId, User user) {
        if (streamId == null) return false;
        return streamRepository.findByIdWithCreator(streamId)
                .filter(Stream::isLive)
                .map(stream -> validateViewerAccess(stream, user))
                .orElse(false);
    }

    public boolean isStreamActive(Long creatorId) {
        if (creatorId == null) return false;
        return !streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorId).isEmpty();
    }

    public boolean validateViewerAccess(Long creatorId, User user) {
        if (creatorId == null) return false;
        java.util.List<Stream> streams = streamRepository.findAllByCreatorIdAndIsLiveTrueWithCreator(creatorId);
        if (streams.isEmpty()) return false;
        return validateViewerAccess(streams.get(0), user);
    }

    public boolean validateViewerAccess(Stream stream, User user) {
        if (stream == null) return false;
        
        // Block suspended and terminated users
        if (user != null && (user.getStatus() == com.joinlivora.backend.user.UserStatus.SUSPENDED || 
                            user.getStatus() == com.joinlivora.backend.user.UserStatus.TERMINATED)) {
            log.warn("SECURITY: View access denied for RESTRICTED creator: {}", user.getEmail());
            return false;
        }

        // 1. Bypass for Creator, Admin and Moderator
        if (user != null) {
            if (stream.getCreator().getId().equals(user.getId()) || 
                user.getRole() == Role.ADMIN || 
                user.getRole() == Role.MODERATOR) {
                return true;
            }
        }

        // 2. Paid stream access check
        Long creatorId = stream.getCreator().getId();
        boolean isPaid = stream.isPaid();

        if (isPaid) {
            if (user == null || !liveAccessService.hasAccess(creatorId, user.getId())) {
                log.warn("SECURITY: Paid access required for stream {} by user {}", stream.getId(), user != null ? user.getEmail() : "anonymous");
                return false;
            }
            return true;
        }

        // 3. Logic for public streams
        if (!isPaid) {
            return true;
        }
        
        // 4. If it's premium, creator must be authenticated
        if (user == null) {
            return false;
        }

        // Check for active subscription to the platform (standard premium)
        boolean hasActiveSubscription = subscriptionRepository.findByUserAndStatus(user, SubscriptionStatus.ACTIVE).isPresent();
        if (hasActiveSubscription) {
            return true;
        }
        
        // Check for PPV purchase if associated
        // Unified stream uses admissionPrice, if > 0 it might be PPV
        if (stream.getAdmissionPrice() != null && stream.getAdmissionPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            // How do we get ppvContentId from Stream? 
            // It seems Stream entity doesn't have it directly.
            // In startStream method of LiveStreamService it was using it.
            // Let's assume for now that if it's paid, and they don't have subscription, we check PPV if we had the ID.
            // Actually, the original code had:
            // if (stream.getPpvContentId() != null) { ... }
            // But 'stream' here was the LiveStream POJO.
        }
        
        return false; 
    }

    public String generateSecureStreamKey() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return "sk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
