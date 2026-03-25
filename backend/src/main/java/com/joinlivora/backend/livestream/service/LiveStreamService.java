package com.joinlivora.backend.livestream.service;

import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.livestream.domain.LiveStreamState;
import com.joinlivora.backend.livestream.websocket.SignalingMessage;
import com.joinlivora.backend.monetization.PPVPurchaseValidationService;
import com.joinlivora.backend.monetization.PpvService;
import com.joinlivora.backend.payment.SubscriptionStatus;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.client.MediasoupClient;
import com.joinlivora.backend.streaming.service.LivestreamAnalyticsService;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.RealtimeMessage;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service("liveStreamServiceV2")
@Slf4j
public class LiveStreamService {

    private final CreatorProfileService creatorProfileService;
    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final LiveViewerCounterService liveViewerCounterService;
    private final StreamRepository streamRepository;
    private final com.joinlivora.backend.livestream.repository.LivestreamSessionRepository livestreamSessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PpvService ppvService;
    private final PPVPurchaseValidationService purchaseValidationService;
    private final MeterRegistry meterRegistry;
    private final LivestreamAnalyticsService analyticsService;
    private final LiveAccessService liveAccessService;
    private final CreatorVerificationRepository creatorVerificationRepository;
    private final MediasoupClient mediasoupClient;
    private final SecureRandom secureRandom = new SecureRandom();

    private AtomicLong activeStreamsGauge;

    public LiveStreamService(
            CreatorProfileService creatorProfileService,
            @Lazy ChatRoomService chatRoomService,
            @Lazy SimpMessagingTemplate messagingTemplate,
            @Lazy LiveViewerCounterService liveViewerCounterService,
            StreamRepository streamRepository,
            com.joinlivora.backend.livestream.repository.LivestreamSessionRepository livestreamSessionRepository,
            ApplicationEventPublisher eventPublisher,
            UserRepository userRepository,
            UserSubscriptionRepository subscriptionRepository,
            @Lazy PpvService ppvService,
            @Lazy PPVPurchaseValidationService purchaseValidationService,
            MeterRegistry meterRegistry,
            LivestreamAnalyticsService analyticsService,
            LiveAccessService liveAccessService,
            CreatorVerificationRepository creatorVerificationRepository,
            MediasoupClient mediasoupClient) {
        this.creatorProfileService = creatorProfileService;
        this.chatRoomService = chatRoomService;
        this.messagingTemplate = messagingTemplate;
        this.liveViewerCounterService = liveViewerCounterService;
        this.streamRepository = streamRepository;
        this.livestreamSessionRepository = livestreamSessionRepository;
        this.eventPublisher = eventPublisher;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.ppvService = ppvService;
        this.purchaseValidationService = purchaseValidationService;
        this.meterRegistry = meterRegistry;
        this.analyticsService = analyticsService;
        this.liveAccessService = liveAccessService;
        this.creatorVerificationRepository = creatorVerificationRepository;
        this.mediasoupClient = mediasoupClient;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        activeStreamsGauge = meterRegistry.gauge("streaming.active.streams", new AtomicLong(0));
        long initialCount = streamRepository.countByIsLiveTrue();
        activeStreamsGauge.set(initialCount);
    }

    /**
     * Starts a live stream for a creator.
     * Validates that the creator is active, public, and doesn't already have an active stream.
     */
    @Transactional
    @CacheEvict(value = "active_streams", key = "#creatorUserId")
    public Stream startLiveStream(Long creatorUserId) {
        // 1. Validate creator status and visibility
        CreatorProfile profile = creatorProfileService.getCreatorByUserId(creatorUserId);
        if (!creatorProfileService.isVisibleToPublic(profile)) {
            throw new IllegalStateException("Creator is not eligible to go live (must be active and public)");
        }

        // 2. Ensure a unified Stream entity is checked
        java.util.List<Stream> liveStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId);
        
        if (!liveStreams.isEmpty()) {
            Stream stream = liveStreams.get(0);
            log.debug("LIVESTREAM: Unified Stream already LIVE for creator {}", creatorUserId);
            // Sync chat room live flag just in case
            chatRoomService.setCreatorRoomLiveStatus(creatorUserId, true);
            
            // Broadcast state
            broadcastStreamState(creatorUserId, true);
            
            return stream;
        }

        log.error("LIVESTREAM: No active unified Stream found for creator {} when starting stream", creatorUserId);
        throw new ResourceNotFoundException("No active stream identity found for creator: " + creatorUserId);
    }

    @Transactional
    public String startStream(Long creatorId, String title, boolean isPremium, java.util.UUID ppvContentId, boolean recordingEnabled) {
        log.info("STREAM: Preparing stream for creatorId: {}", creatorId);
        
        java.util.List<Stream> streams = streamRepository.findAllByCreatorIdAndIsLiveTrueWithCreator(creatorId);
        Stream stream = streams.isEmpty() ? null : streams.get(0);

        String streamKey = stream != null ? stream.getStreamKey() : generateSecureStreamKey();
        
        log.info("STREAM: Creator {} is ready to start with key {} (recording={})", creatorId, streamKey, recordingEnabled);
        return streamKey;
    }

    @Transactional
    public boolean verifyStreamKeyAndStart(String streamKey) {
        log.info("RTMP: Verifying stream key: {}", streamKey);
        
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
            boolean isReconnecting = stream.isLive();
            if (isReconnecting) {
                log.info("RTMP: Stream for creator {} is already marked LIVE. Allowing re-connection.", 
                        stream.getCreator().getId());
            }

            stream.setLive(true);
            stream.setStartedAt(Instant.now());
            stream.setEndedAt(null);
            streamRepository.save(stream);
            log.info("RTMP: Stream key verified via Unified Stream, creator={}, stream {} is now LIVE", stream.getCreator().getId(), stream.getId());

            if (!isReconnecting) {
                cleanupMediasoupRoom(stream.getMediasoupRoomId());
            } else {
                log.info("RTMP: Reconnection detected for stream {}. Skipping Mediasoup room cleanup to preserve viewer sessions.", stream.getId());
            }
            
            // Sync chat room status and broadcast state
            chatRoomService.setCreatorRoomLiveStatus(stream.getCreator().getId(), true);
            broadcastStreamState(stream.getCreator().getId(), true);
            
            return true;
        }

        return false;
    }

    @Transactional
    public void verifyStreamKeyAndStop(String streamKey) {
        log.info("RTMP: Ending stream for key: {}", streamKey);
        
        streamRepository.findByStreamKeyWithCreator(streamKey).ifPresent(stream -> {
            stopLiveStreamInternal(stream, "disconnect");
        });
    }

    @Transactional
    @CacheEvict(value = "active_streams", key = "#creatorUserId")
    public Stream stopLiveStream(Long creatorUserId) {
        java.util.List<Stream> liveStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId);
        if (liveStreams.isEmpty()) {
            throw new ResourceNotFoundException("No active unified stream found for creator ID: " + creatorUserId);
        }
        Stream stream = liveStreams.get(0);

        return stopLiveStreamInternal(stream);
    }

    private Stream stopLiveStreamInternal(Stream stream) {
        return stopLiveStreamInternal(stream, "creator");
    }

    private Stream stopLiveStreamInternal(Stream stream, String reason) {
        Long creatorUserId = stream.getCreator().getId();
        log.info("LIVESTREAM: Marking unified Stream as ENDED for creator ID: {}", creatorUserId);
        stream.setLive(false);
        stream.setEndedAt(Instant.now());
        streamRepository.save(stream);

        // Sync with chat room
        chatRoomService.setCreatorRoomLiveStatus(creatorUserId, false);
        
        Long streamSessionId = null;
        
        // End LivestreamSession
        var sessionOpt = livestreamSessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(creatorUserId, com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE);
        if (sessionOpt.isPresent()) {
            var session = sessionOpt.get();
            session.end();
            livestreamSessionRepository.save(session);
            streamSessionId = session.getId();
            log.info("LIVESTREAM: Ended LivestreamSession {} for creator {}", session.getId(), creatorUserId);
            eventPublisher.publishEvent(new com.joinlivora.backend.livestream.event.StreamEndedEvent(this, session, reason, stream.getId()));
        }
        
        // Notify viewers and cleanup (logic moved from V1 notifyStreamStop)
        notifyStreamStop(stream, streamSessionId);
        
        // Broadcast state change
        broadcastStreamState(creatorUserId, false);
        
        return stream;
    }

    private void notifyStreamStop(Stream stream, Long streamSessionId) {
        if (stream == null) return;
        Long creatorId = stream.getCreator().getId();
        UUID roomId = stream.getMediasoupRoomId();
        
        // 1. Reset viewer count in Redis (MANDATORY cleanup)
        if (streamSessionId != null) {
            liveViewerCounterService.resetViewerCount(streamSessionId, creatorId);
        } else {
            log.warn("LIVESTREAM: No streamSessionId found during notifyStreamStop for creator {}. Key cleanup may be incomplete.", creatorId);
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
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, 
                Map.of("type", "STREAM_STOP", "creatorId", creatorId));
        }
    }

    public void cleanupMediasoupRoom(UUID roomId) {
        if (roomId != null) {
            mediasoupClient.closeRoom(roomId);
        }
    }

    @Transactional
    public void updateRecordingPath(String streamKey, String path) {
        log.info("RTMP: Recording finished for key: {}, path: {}", streamKey, path);
        
        streamRepository.findByStreamKeyWithCreator(streamKey).ifPresent(stream -> {
            log.info("RTMP: Recording path updated for unified stream {}: {}", stream.getId(), path);
        });
    }

    @Transactional
    public void stopStream(Long creatorId) {
        stopStream(creatorId, "creator");
    }

    @Transactional
    public void stopStream(Long creatorId, String reason) {
        log.info("Stopping live stream for creatorUserId: {} reason: {}", creatorId, reason);
        
        java.util.List<Stream> streams = streamRepository.findAllByCreatorIdAndIsLiveTrueWithCreator(creatorId);
        if (!streams.isEmpty()) {
            stopLiveStreamInternal(streams.get(0), reason);
        }
    }

    public boolean isStreamActive(Long creatorId) {
        if (creatorId == null) return false;
        return !streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorId).isEmpty();
    }

    @Cacheable(
            value = "viewer_access",
            key = "#streamId.toString() + ':' + (#user != null ? #user.id : 'anonymous')",
            unless = "#result == false"
    )
    public boolean validateViewerAccess(UUID streamId, User user) {
        if (streamId == null) return false;
        return streamRepository.findByIdWithCreator(streamId)
                .filter(Stream::isLive)
                .map(stream -> validateViewerAccess(stream, user))
                .orElse(false);
    }

    public boolean validateViewerAccess(Long creatorId, User user) {
        if (creatorId == null) return false;
        java.util.List<Stream> streams = streamRepository.findAllByCreatorIdAndIsLiveTrueWithCreator(creatorId);
        if (streams.isEmpty()) return false;
        return validateViewerAccess(streams.get(0), user);
    }

    public boolean validateViewerAccess(Stream stream, User user) {
        if (stream == null) return false;
        
        if (user != null && (user.getStatus() == com.joinlivora.backend.user.UserStatus.SUSPENDED || 
                            user.getStatus() == com.joinlivora.backend.user.UserStatus.TERMINATED)) {
            log.warn("SECURITY: View access denied for RESTRICTED user: {}", user.getEmail());
            return false;
        }

        if (user != null) {
            if (stream.getCreator().getId().equals(user.getId()) || 
                user.getRole() == Role.ADMIN || 
                user.getRole() == Role.MODERATOR) {
                return true;
            }
        }

        Long creatorId = stream.getCreator().getId();
        boolean isPaid = stream.isPaid();

        if (isPaid) {
            if (user == null || !liveAccessService.hasAccess(creatorId, user.getId())) {
                log.warn("SECURITY: Paid access required for stream {} by user {}", stream.getId(), user != null ? user.getEmail() : "anonymous");
                return false;
            }
            return true;
        }

        if (!isPaid) {
            return true;
        }
        
        if (user == null) {
            return false;
        }

        boolean hasActiveSubscription = subscriptionRepository.findByUserAndStatus(user, SubscriptionStatus.ACTIVE).isPresent();
        if (hasActiveSubscription) {
            return true;
        }
        
        return false; 
    }

    public String generateSecureStreamKey() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return "sk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private void broadcastStreamState(Long creatorUserId, boolean isLive) {
        RealtimeMessage message = RealtimeMessage.of("stream:state:update", Map.of(
                "creatorUserId", creatorUserId,
                "isLive", isLive
        ));
        messagingTemplate.convertAndSend("/exchange/amq.topic/stream.v2.creator." + creatorUserId + ".status", message);
    }

    /**
     * Retrieves the current active live stream for a creator.
     */
    @Cacheable(
        value = "active_streams",
        key = "#creatorId"
    )
    public Stream getActiveStream(Long creatorId) {
        java.util.List<Stream> streams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorId);
        return streams.isEmpty() ? null : streams.get(0);
    }
}
