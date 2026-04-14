package com.joinlivora.backend.streaming;

import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.domain.ChatRoomStatus;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.creator.service.OnlineStatusService;
import com.joinlivora.backend.streaming.StreamRoom;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import com.joinlivora.backend.livestream.event.StreamStartedEventV2;
import org.springframework.context.ApplicationEventPublisher;
import com.joinlivora.backend.streaming.dto.GoLiveRequest;
import com.joinlivora.backend.streaming.StreamCacheDTO;
import com.joinlivora.backend.streaming.service.StreamCacheService;
import com.joinlivora.backend.streaming.transcode.TranscodeJobPublisher;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Unified entrypoint for transitioning a creator to LIVE state across subsystems.
 *
 * Responsibilities (in order):
 *  1) Mark presence ONLINE (DB + Redis TTL when available)
 *  2) Start or resume the LiveStream (V2 domain) if not already active
 *  3) Activate any WAITING/PAUSED chat rooms (Chat V2)
 *  4) Broadcast presence + stream/chat state updates over WebSocket
 *
 * NOTE: This service should be the ONLY entrypoint for going live.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorGoLiveService {

    private final CreatorPresenceService creatorPresenceService;
    private final OnlineStatusService onlineStatusService;
    private final com.joinlivora.backend.livestream.service.LiveStreamService liveStreamService;
    private final ChatRoomService chatRoomServiceV2;
    private final CreatorRepository creatorRepository;
    private final StreamRepository streamRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.joinlivora.backend.streaming.service.LiveViewerCounterService liveViewerCounterService;
    private final AdminRealtimeEventService adminRealtimeEventService;
    private final StreamAssistantBotService streamAssistantBotService;
    private final TranscodeJobPublisher transcodeJobPublisher;
    private final StreamCacheService streamCacheService;

    /**
     * Mark a creator as online across subsystems without starting a live session.
     */
    @Transactional
    public void markOnline(Long creatorId) {
        if (creatorId == null) return;
        
        try {
            if (onlineStatusService != null && onlineStatusService.isAvailable()) {
                onlineStatusService.setOnline(creatorId);
            }
        } catch (Exception e) {
            log.warn("PRESENCE: Failed to mark creator {} online: {}", creatorId, e.getMessage());
        }
    }

    /**
     * Executes the go-live sequence for the given creator entity ID (Creator.id).
     * Returns the list of chat rooms that were activated as part of this action.
     */
    @Transactional
    public List<ChatRoom> goLive(Long creatorId) {
        return goLive(creatorId, null);
    }

    /**
     * Executes the go-live sequence with optional configuration.
     */
    @Transactional
    public List<ChatRoom> goLive(Long creatorId, GoLiveRequest request) {
        if (creatorId == null) {
            throw new IllegalArgumentException("creator is required");
        }

        // Resolve creator -> userId for broadcasting to creator-scoped topics
        var creator = creatorRepository.findById(creatorId)
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("Creator not found with ID: " + creatorId));
        Long creatorUserId = creator.getUser().getId();

        // 1. Guard: Prevent duplicate active streams or cleanup stale state
        List<Stream> liveStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId);
        boolean streamExists = !liveStreams.isEmpty();

        // TASK 4: If multiple live streams exist, clean up duplicates — keep newest only
        if (liveStreams.size() > 1) {
            log.warn("Multiple live streams detected for creator {} — found {}. Cleaning up duplicates.", creatorId, liveStreams.size());
            for (int i = 1; i < liveStreams.size(); i++) {
                Stream stale = liveStreams.get(i);
                stale.setLive(false);
                stale.setEndedAt(Instant.now());
                streamRepository.save(stale);
                log.info("STREAM_DUPLICATE_CLEANUP: Ended stale stream {} for creator {}", stale.getId(), creatorId);
            }
        }

        Stream newestStream = streamExists ? liveStreams.get(0) : null;

        if (streamExists) {
            log.info("STREAM_IDEMPOTENT: Active unified stream already exists for creator {}, returning rooms.", creatorId);
            if (newestStream.getMediasoupRoomId() == null) {
                newestStream.setMediasoupRoomId(java.util.UUID.randomUUID());
                streamRepository.save(newestStream);
            }
            return chatRoomServiceV2.activateWaitingRooms(creatorId);
        }

        // 2. Ensure LiveStream exists to have a persistent streamKey
        String streamKey = liveStreamService.startStream(
                creatorUserId,
                request != null ? request.getTitle() : "My Stream",
                false, // isPremium
                null,  // ppvContentId
                request != null && request.isRecordingEnabled()
        );

        // 3. Update StreamRoom metadata
        boolean isPaid = request != null && request.isPaid();
        BigDecimal admissionPrice = (request != null && isPaid) ? request.getAdmissionPrice() : BigDecimal.ZERO;

        if (request != null && request.isPaid()) {
            if (request.getAdmissionPrice() == null || request.getAdmissionPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Paid stream requires valid admission price");
            }
        }

        log.info("STREAM: Syncing metadata for creator {}", creatorId);
        // Unified go-live flow creates the Stream entity which contains all necessary metadata
        String thumbnailPath = "/thumbnails/" + streamKey + ".jpg";
        
        // 4. Create unified Stream entity
        Stream unifiedStream = Stream.builder()
                .creator(creator.getUser())
                .title(request != null ? request.getTitle() : "My Stream")
                .isLive(true)
                .startedAt(Instant.now())
                .mediasoupRoomId(java.util.UUID.randomUUID())
                .streamKey(streamKey)
                .isPaid(isPaid)
                .admissionPrice(admissionPrice)
                .chatEnabled(request != null ? request.isChatEnabled() : true)
                .slowMode(request != null ? request.isSlowMode() : false)
                .slowModeInterval(request != null ? request.getSlowModeInterval() : null)
                .maxViewers(request != null ? request.getMaxViewers() : null)
                .streamCategory(request != null ? request.getStreamCategory() : null)
                .thumbnailUrl(thumbnailPath)
                .build();
        streamRepository.saveAndFlush(unifiedStream);
        log.info("STREAM_LIVE: streamId={}, creatorId={}", unifiedStream.getId(), creatorId);
        log.info("STREAM_IDENTITY_CREATED: creatorId={}, streamId={}", creatorId, unifiedStream.getId());
        // Initialize UUID-based active stream pointer in Redis so viewer counter uses the new key format
        liveViewerCounterService.setActiveStreamUuid(creatorUserId, unifiedStream.getId());

        // 5. Publish V2 event (UUID-based only — legacy StreamStartedEvent removed)
        log.info("Publishing StreamStartedEventV2 for streamId={} creatorUserId={}", unifiedStream.getId(), creatorUserId);
        eventPublisher.publishEvent(new StreamStartedEventV2(this,
                unifiedStream.getId(),
                creatorUserId,
                unifiedStream.isPaid(),
                unifiedStream.getAdmissionPrice(),
                unifiedStream.getStartedAt()));

        // 6. Populate streams:live Redis ZSET cache (non-fatal)
        try {
            StreamCacheDTO cacheDto = StreamCacheDTO.builder()
                    .id(unifiedStream.getId())
                    .creatorUserId(creatorUserId)
                    .title(unifiedStream.getTitle())
                    .isLive(true)
                    .startedAt(unifiedStream.getStartedAt())
                    .streamKey(unifiedStream.getStreamKey())
                    .thumbnailUrl(unifiedStream.getThumbnailUrl())
                    .mediasoupRoomId(unifiedStream.getMediasoupRoomId())
                    .admissionPrice(unifiedStream.getAdmissionPrice())
                    .build();
            streamCacheService.addStream(cacheDto, unifiedStream.getStartedAt());
        } catch (Exception e) {
            log.warn("StreamCache: failed to cache stream {} — explore page will fall back to DB: {}",
                    unifiedStream.getId(), e.getMessage());
        }

        // 7. Enqueue GPU transcode job (non-fatal — WebRTC stream runs regardless)
        transcodeJobPublisher.publishStartJob(unifiedStream.getId(), creatorUserId, streamKey);

        // 1) Presence ONLINE
        markOnline(creatorId);

        // 2) Start or resume LiveStream if not already LIVE
        // Simplified: call the service directly in the same transaction to ensure visibility
        liveStreamService.startLiveStream(creatorUserId);

        // 3) Activate WAITING/PAUSED chat rooms
        List<ChatRoom> activatedRooms = java.util.Collections.emptyList();
        try {
            activatedRooms = chatRoomServiceV2.activateWaitingRooms(creatorId);
        } catch (Exception e) {
            log.error("CHAT-V2: Error activating rooms for creator {}: {}", creatorId, e.getMessage());
        }

        // 4) Broadcast presence + stream/chat state updates handled by StreamStartedEvent listener
        try {
            // Creator joined (once per connect/go-live trigger)
            RealtimeMessage joinedEvent = RealtimeMessage.of("chat:creator:joined", Map.of(
                    "creatorUserId", creatorUserId,
                    "creator", creatorId
            ));
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat.v2.creator." + creatorUserId + ".status", joinedEvent);

            for (ChatRoom room : activatedRooms) {
                log.info("CHAT-V2: Room {} activated for creatorUserId {} (creator={})", room.getId(), creatorUserId, creatorId);
                RealtimeMessage stateUpdate = RealtimeMessage.builder()
                        .type("chat:state:update")
                        .timestamp(Instant.now())
                        .payload(Map.of(
                                "creatorUserId", creatorUserId,
                                "creator", creatorId,
                                "roomId", room.getId(),
                                "status", ChatRoomStatus.ACTIVE.name()
                        ))
                        .build();
                messagingTemplate.convertAndSend("/exchange/amq.topic/chat.v2.creator." + creatorUserId + ".status", stateUpdate);
            }
        } catch (Exception e) {
            log.error("WS: Error broadcasting go-live updates for creator {}: {}", creatorId, e.getMessage());
        }

        // 5) Bot stream start announcement — disabled: creator should not receive self-referential system messages

        return activatedRooms;
    }

}
