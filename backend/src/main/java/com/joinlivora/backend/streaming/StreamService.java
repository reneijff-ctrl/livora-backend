package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamService {

    private final StreamRoomRepository streamRoomRepository;
    private final UserService userService;
    private final AnalyticsEventPublisher analyticsEventPublisher;

    public List<StreamRoom> getLiveStreams() {
        return streamRoomRepository.findAllByIsLiveTrue();
    }

    public StreamRoom getRoom(UUID id) {
        return streamRoomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stream room not found"));
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
    public StreamRoom startStream(User creator, String title, String description, Long minChatTokens) {
        StreamRoom room = getCreatorRoom(creator);
        room.setStreamTitle(title);
        room.setDescription(description);
        room.setMinChatTokens(minChatTokens);
        room.setLive(true);
        room.setStartedAt(Instant.now());
        room.setEndedAt(null);
        
        StreamRoom saved = streamRoomRepository.save(room);
        log.info("STREAM: Creator {} started stream {}", creator.getEmail(), saved.getId());
        
        // Broadcast via WebSocket would normally be here or via event publisher
        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.VISIT, // Placeholder for stream event
                creator,
                Map.of("roomId", saved.getId(), "action", "STREAM_START", "title", title)
        );
        
        return saved;
    }

    @Transactional
    public StreamRoom stopStream(User creator) {
        StreamRoom room = getCreatorRoom(creator);
        room.setLive(false);
        room.setEndedAt(Instant.now());
        room.setViewerCount(0);
        
        StreamRoom saved = streamRoomRepository.save(room);
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
        streamRoomRepository.findById(roomId).ifPresent(room -> {
            room.setViewerCount(Math.max(0, room.getViewerCount() + delta));
            streamRoomRepository.save(room);
        });
    }
}
