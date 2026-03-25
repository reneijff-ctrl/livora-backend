package com.joinlivora.backend.streaming;

import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Disabled("Legacy streaming architecture")
@ExtendWith(MockitoExtension.class)
class StreamServiceModerationTest {

    @Mock
    private com.joinlivora.backend.user.UserRepository userRepository;
    @Mock
    private StreamRepository streamRepository;
    @Mock
    private LiveStreamService liveStreamServiceMock;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private com.joinlivora.backend.chat.service.ChatRoomService chatRoomService;
    @Mock
    private com.joinlivora.backend.user.UserService userService;
    @Mock
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;
    @Mock
    private com.joinlivora.backend.token.TokenService tokenService;
    @Mock
    private LiveViewerCounterService liveViewerCounterService;
    @Mock
    private CreatorGoLiveService creatorGoLiveService;
    @Mock
    private com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository;
    @Mock
    private com.joinlivora.backend.creator.verification.CreatorVerificationRepository creatorVerificationRepository;
    @Mock
    private AdminRealtimeEventService adminRealtimeEventService;
    @Mock
    private com.joinlivora.backend.fraud.service.FraudRiskScoreService fraudRiskScoreService;

    @InjectMocks
    private StreamService streamService;

    private UUID roomId;
    private Stream room;
    private User creator;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@test.com");
        
        room = Stream.builder()
                .id(roomId)
                .creator(creator)
                .isLive(true)
                .slowMode(false)
                .build();
    }

    @Test
    void getActiveRooms_ShouldReturnLiveRooms() {
        when(streamRepository.findActiveStreamsWithUser()).thenReturn(List.of(room));
        
        List<StreamRoom> activeRooms = streamService.getActiveRooms();
        
        assertEquals(1, activeRooms.size());
        assertTrue(activeRooms.get(0).isLive());
    }

    @Test
    void closeRoom_ShouldStopStream_WhenLive() {
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
        when(streamRepository.findAllByCreatorAndIsLiveTrueOrderByStartedAtDesc(creator)).thenReturn(List.of(room));
        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creator.getId())).thenReturn(List.of(room));
        when(streamRepository.save(any(Stream.class))).thenReturn(room);

        streamService.closeRoom(roomId);
        
        verify(liveStreamServiceMock).stopStream(creator.getId());
        verify(streamRepository, atLeastOnce()).save(room);
    }

    @Test
    void setSlowMode_ShouldEnableSlowMode_AndBroadcast() {
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));

        streamService.setSlowMode(roomId, true);
        
        assertTrue(room.isSlowMode());
        verify(streamRepository).save(room);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creator.getId()), any(Object.class));
    }

    @Test
    void setSlowMode_ShouldDisableSlowMode_AndBroadcast() {
        room.setSlowMode(true);
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));

        streamService.setSlowMode(roomId, false);
        
        assertFalse(room.isSlowMode());
        verify(streamRepository).save(room);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creator.getId()), any(Object.class));
    }
}









