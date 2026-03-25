package com.joinlivora.backend.streaming;

import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.payment.SubscriptionStatus;
import com.joinlivora.backend.payment.UserSubscription;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import com.joinlivora.backend.streaming.service.LivestreamAnalyticsService;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.websocket.PresenceService;
import com.joinlivora.backend.websocket.RealtimeMessage;
import com.joinlivora.backend.livestream.websocket.SignalingMessage;
import com.joinlivora.backend.monetization.PPVPurchaseValidationService;
import com.joinlivora.backend.monetization.PpvService;
import com.joinlivora.backend.livestream.service.LiveStreamService;
import com.joinlivora.backend.livestream.repository.LivestreamSessionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Disabled("Legacy streaming architecture")
@ExtendWith(MockitoExtension.class)
class StreamIntegrationTest {

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.joinlivora.backend.chat.service.ChatRoomService chatRoomService;

    @Mock
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @Mock
    private com.joinlivora.backend.streaming.CreatorGoLiveService creatorGoLiveService;

    @Mock
    private com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository;

    @Mock
    private LiveViewerCounterService liveViewerCounterService;

    @Mock
    private LiveAccessService liveAccessService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserSubscriptionRepository subscriptionRepository;

    @Mock
    private LiveStreamService liveStreamService;

    @Mock
    private LivestreamAnalyticsService analyticsService;

    @Mock
    private com.joinlivora.backend.token.TokenService tokenService;

    @Mock
    private com.joinlivora.backend.creator.verification.CreatorVerificationRepository creatorVerificationRepository;
    
    @Mock
    private PpvService ppvService;

    @Mock
    private PPVPurchaseValidationService purchaseValidationService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private LivestreamSessionRepository livestreamSessionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AdminRealtimeEventService adminRealtimeEventService;

    @Mock
    private com.joinlivora.backend.fraud.service.FraudRiskScoreService fraudRiskScoreService;

    @Mock
    private com.joinlivora.backend.streaming.service.StreamAssistantBotService streamAssistantBotService;

    @InjectMocks
    private StreamService streamService;

    @InjectMocks
    private PresenceService presenceService;

    private User creator;
    private User viewer;
    private Stream premiumStream;
    private Stream room;
    private UUID liveStreamId;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creatorUserId@test.com");
        creator.setPayoutsEnabled(true);

        viewer = new User();
        viewer.setId(2L);
        viewer.setEmail("viewer@test.com");

        liveStreamId = UUID.randomUUID();
        
        // Mock verification status as APPROVED by default for integration tests
        com.joinlivora.backend.creator.model.CreatorVerification verification = 
            com.joinlivora.backend.creator.model.CreatorVerification.builder()
                .status(com.joinlivora.backend.creator.verification.VerificationStatus.APPROVED)
                .build();
        lenient().when(creatorVerificationRepository.findByCreatorId(anyLong())).thenReturn(Optional.of(verification));

        premiumStream = Stream.builder()
                .id(liveStreamId)
                .creator(creator)
                .isLive(true)
                .isPaid(true)
                .build();

        room = Stream.builder()
                .id(liveStreamId)
                .creator(creator)
                .isLive(true)
                .isPaid(true)
                .viewerCount(0)
                .build();

        streamService = new StreamService(
                userRepository,
                liveStreamService,
                chatRoomService,
                userService,
                analyticsEventPublisher,
                messagingTemplate,
                creatorGoLiveService,
                creatorRepository,
                tokenService,
                liveViewerCounterService,
                creatorVerificationRepository,
                adminRealtimeEventService,
                streamRepository,
                fraudRiskScoreService,
                streamAssistantBotService
        );
        
        // Use reflection to set the streamService in presenceService
        try {
            java.lang.reflect.Field field = PresenceService.class.getDeclaredField("streamService");
            field.setAccessible(true);
            field.set(presenceService, streamService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testScenario1_ViewerWithoutPayment_StreamDenied() {
        when(subscriptionRepository.findByUserAndStatus(viewer, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());

        boolean access = liveStreamService.validateViewerAccess(premiumStream, viewer);
        assertFalse(access, "Viewer without payment should be denied access to premium liveStream");
    }

    @Test
    void testScenario2_ViewerWithPayment_StreamAllowed() {
        when(subscriptionRepository.findByUserAndStatus(viewer, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(new UserSubscription()));

        boolean access = liveStreamService.validateViewerAccess(premiumStream, viewer);
        assertTrue(access, "Viewer with payment should be allowed access to premium liveStream");
    }

    @Test
    void testScenario3_CreatorStopsStream_ViewersDisconnected() {
        when(streamRepository.findByCreator(creator)).thenReturn(Optional.of(room));
        when(streamRepository.save(any(Stream.class))).thenReturn(room);
        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creator.getId())).thenReturn(java.util.List.of(premiumStream));
        when(streamRepository.save(any(Stream.class))).thenReturn(premiumStream);
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));

        streamService.stopStream(creator);

        verify(chatRoomService).deleteRoom("liveStream-" + liveStreamId);
        assertFalse(room.isLive());
        assertFalse(premiumStream.isLive());
        verify(messagingTemplate, atLeastOnce()).convertAndSend(contains("/chat"), any(RealtimeMessage.class));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(contains("/video"), any(SignalingMessage.class));
    }

    @Test
    void testScenario6_OBSDisconnects_MarksOfflineAndNotifies() {
        String key = "sk_disconnect_test";
        premiumStream.setStreamKey(key);
        premiumStream.setLive(true);
        room.setLive(true);

        when(streamRepository.findByStreamKey(key)).thenReturn(Optional.of(premiumStream));
        when(streamRepository.save(any(Stream.class))).thenReturn(premiumStream);
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
        when(streamRepository.findByCreator(creator)).thenReturn(Optional.of(room));
        when(streamRepository.save(any(Stream.class))).thenReturn(room);

        liveStreamService.verifyStreamKeyAndStop(key);

        assertFalse(premiumStream.isLive(), "Stream should be offline after OBS disconnect");
        assertFalse(room.isLive(), "Room should be offline after OBS disconnect");
        verify(chatRoomService).deleteRoom("liveStream-" + liveStreamId);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(contains("/chat"), any(RealtimeMessage.class));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(contains("/video"), any(SignalingMessage.class));
    }

    @Test
    void testScenario5_RTMPAuth_ShouldMarkStreamLive() {
        String key = "sk_test_123";
        Stream liveStream = Stream.builder()
                .id(liveStreamId)
                .creator(creator)
                .streamKey(key)
                .isLive(false)
                .build();
        
        when(streamRepository.findByStreamKey(key)).thenReturn(Optional.of(liveStream));
        when(streamRepository.save(any(Stream.class))).thenReturn(liveStream);
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
        when(streamRepository.findByCreator(creator)).thenReturn(Optional.of(room));
        room.setLive(false);

        boolean authorized = liveStreamService.verifyStreamKeyAndStart(key);

        assertTrue(authorized);
        assertTrue(liveStream.isLive());
        assertTrue(room.isLive());
        verify(streamRepository).save(liveStream);
        verify(streamRepository).save(room);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testScenario4_ViewerCountUpdatesCorrectly() {
        String sessionId = "session-123";
        String destination = "/exchange/amq.topic/liveStream." + liveStreamId + ".video";
        
        Message<byte[]> message = (Message<byte[]>) mock(Message.class);
        Map<String, Object> headers = new HashMap<>();
        headers.put("simpDestination", destination);
        headers.put("simpSessionId", sessionId);
        headers.put("simpSubscriptionId", "sub-1");
        doReturn(new MessageHeaders(headers)).when(message).getHeaders();
        
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, message);
        
        when(streamRepository.findById(liveStreamId)).thenReturn(Optional.of(room));

        presenceService.handleSubscriptionEvent(event);
        
        verify(streamRepository).save(argThat(r -> r.getViewerCount() == 1));
    }
}






