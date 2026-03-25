package com.joinlivora.backend.websocket;

import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.livestream.service.LiveStreamService;
import com.joinlivora.backend.streaming.StreamService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.creator.model.Creator;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.presence.service.OnlineCreatorRegistry;
import com.joinlivora.backend.creator.service.OnlineStatusService;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.streaming.CreatorGoLiveService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceChatTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private StreamService streamService;
    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock
    private UserService userService;
    @Mock
    private OnlineStatusService onlineStatusService;
    @Mock
    private CreatorPresenceService creatorPresenceService;
    @Mock
    private OnlineCreatorRegistry onlineCreatorRegistry;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private ChatRoomRepository chatRoomRepositoryV2;
    @Mock
    private ChatRoomService chatRoomServiceV2;
    @Mock
    private CreatorGoLiveService creatorGoLiveService;
    @Mock
    private LiveViewerCounterService liveViewerCounterService;
    @Mock
    private StreamAssistantBotService streamAssistantBotService;
    @Mock
    private LiveStreamService liveStreamService;

    @Mock
    private com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        lenient().when(userService.getById(anyLong())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            User userMock = new User();
            userMock.setId(userId);
            userMock.setEmail("user" + userId + "@test.com");
            // If ID is 1, 2, or 3, make it a creator (as used in tests)
            if (userId == 1L || userId == 2L || userId == 3L) {
                userMock.setRole(com.joinlivora.backend.user.Role.CREATOR);
            } else {
                userMock.setRole(com.joinlivora.backend.user.Role.USER);
            }
            return userMock;
        });
        lenient().when(creatorProfileService.getCreatorIdByUserId(anyLong())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return Optional.of(userId * 10); // Simulated creatorId
        });

        com.joinlivora.backend.presence.service.SessionRegistryService sessionRegistry = new com.joinlivora.backend.presence.service.SessionRegistryService();
        com.joinlivora.backend.presence.service.PresenceTrackingService presenceTracking = new com.joinlivora.backend.presence.service.PresenceTrackingService(null, onlineStatusService);
        com.joinlivora.backend.presence.service.ViewerCountService viewerCountService = new com.joinlivora.backend.presence.service.ViewerCountService(liveViewerCounterService);
        com.joinlivora.backend.presence.service.PresenceEventOrchestrator eventOrchestrator = new com.joinlivora.backend.presence.service.PresenceEventOrchestrator(
                messagingTemplate, analyticsEventPublisher, streamAssistantBotService, chatRoomServiceV2,
                com.joinlivora.backend.websocket.PresenceService.createAlwaysAvailableBrokerListener(), null);

        presenceService = new PresenceService(
                sessionRegistry,
                presenceTracking,
                viewerCountService,
                eventOrchestrator,
                userService,
                streamService,
                liveStreamService,
                creatorProfileService,
                onlineCreatorRegistry,
                liveViewerCounterService,
                null,  // creatorFollowRepository
                null   // streamModeratorService
        );
    }

    @Test
    void handleDisconnect_CreatorLive_ShouldNotPauseChat() {
        String sessionId = "session-1";
        Long userId = 1L;
        Long creatorId = 10L;
        String principalId = userId.toString();

        // Mock creator session
        User user = new User();
        user.setId(userId);
        user.setEmail("creator@test.com");
        user.setRole(com.joinlivora.backend.user.Role.CREATOR);
        
        Creator creator = new Creator();
        creator.setId(creatorId);
        creator.setUser(user);

        // Set up internal state of presenceService to simulate a creator session
        // We can use ReflectionTestUtils or just trigger a connect first
        // But handleWebSocketDisconnectListener handles it.
        
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        lenient().when(event.getSessionId()).thenReturn(sessionId);
        
        com.joinlivora.backend.websocket.StompPrincipal principal = new com.joinlivora.backend.websocket.StompPrincipal(principalId, userId.toString());
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth = 
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CREATOR")));
        lenient().when(event.getUser()).thenReturn(auth);

        // Mock user and creator lookups
        lenient().when(userService.resolveUserFromSubject(principalId)).thenReturn(Optional.of(user));
        lenient().when(creatorRepository.findByUser_Id(userId)).thenReturn(Optional.of(creator));
        
        // Mock that this is the last session
        // Internal state is not easily mockable without connecting first, so let's connect first
        org.springframework.messaging.Message<byte[]> connectMsg = mock(org.springframework.messaging.Message.class);
        org.springframework.messaging.MessageHeaders headers = new org.springframework.messaging.MessageHeaders(java.util.Map.of("simpSessionId", sessionId));
        lenient().when(connectMsg.getHeaders()).thenReturn(headers);
        lenient().when(creatorPresenceService.getAvailability(userId)).thenReturn(com.joinlivora.backend.presence.model.CreatorAvailabilityStatus.OFFLINE);
        
        presenceService.handleWebSocketConnectListener(new org.springframework.web.socket.messaging.SessionConnectEvent(this, connectMsg, principal));

        // Now test disconnect
        lenient().when(liveStreamService.isStreamActive(userId)).thenReturn(true);

        presenceService.handleWebSocketDisconnectListener(event);

        // Verify that pauseActiveRooms was NEVER called because stream is active
        verify(chatRoomServiceV2, never()).pauseActiveRooms(anyLong());
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.v2.creator." + userId + ".status"), any(RealtimeMessage.class));
    }

    @Test
    void handleDisconnect_CreatorNotLive_ShouldPauseChat() {
        String sessionId = "session-2";
        Long userId = 2L;
        Long creatorId = 20L;
        String principalId = userId.toString();

        User user = new User();
        user.setId(userId);
        user.setEmail("creator2@test.com");
        user.setRole(com.joinlivora.backend.user.Role.CREATOR);
        
        Creator creator = new Creator();
        creator.setId(creatorId);
        creator.setUser(user);

        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        lenient().when(event.getSessionId()).thenReturn(sessionId);
        com.joinlivora.backend.websocket.StompPrincipal principal = new com.joinlivora.backend.websocket.StompPrincipal(principalId, userId.toString());
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth = 
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CREATOR")));
        lenient().when(event.getUser()).thenReturn(auth);

        lenient().when(userService.resolveUserFromSubject(principalId)).thenReturn(Optional.of(user));
        lenient().when(creatorRepository.findByUser_Id(userId)).thenReturn(Optional.of(creator));
        
        org.springframework.messaging.Message<byte[]> connectMsg = mock(org.springframework.messaging.Message.class);
        org.springframework.messaging.MessageHeaders headers = new org.springframework.messaging.MessageHeaders(java.util.Map.of("simpSessionId", sessionId));
        lenient().when(connectMsg.getHeaders()).thenReturn(headers);
        lenient().when(creatorPresenceService.getAvailability(userId)).thenReturn(com.joinlivora.backend.presence.model.CreatorAvailabilityStatus.OFFLINE);
        
        presenceService.handleWebSocketConnectListener(new org.springframework.web.socket.messaging.SessionConnectEvent(this, connectMsg, auth));

        // Now test disconnect
        lenient().when(liveStreamService.isStreamActive(userId)).thenReturn(false);
        lenient().when(chatRoomServiceV2.pauseActiveRooms(creatorId)).thenReturn(List.of());

        presenceService.handleWebSocketDisconnectListener(event);

        // Verify that pauseActiveRooms WAS called because stream is not active
        verify(chatRoomServiceV2).pauseActiveRooms(creatorId);
    }

    @Test
    void handleConnect_CreatorLive_ShouldReactivateChat() {
        String sessionId = "session-3";
        Long userId = 3L;
        Long creatorId = 30L;
        String principalId = userId.toString();

        User user = new User();
        user.setId(userId);
        user.setEmail("creator3@test.com");
        user.setRole(com.joinlivora.backend.user.Role.CREATOR);
        
        Creator creator = new Creator();
        creator.setId(creatorId);
        creator.setUser(user);

        com.joinlivora.backend.websocket.StompPrincipal principal = new com.joinlivora.backend.websocket.StompPrincipal(principalId, userId.toString());
        org.springframework.messaging.Message<byte[]> connectMsg = mock(org.springframework.messaging.Message.class);
        org.springframework.messaging.MessageHeaders headers = new org.springframework.messaging.MessageHeaders(java.util.Map.of("simpSessionId", sessionId));
        lenient().when(connectMsg.getHeaders()).thenReturn(headers);
        lenient().when(creatorPresenceService.getAvailability(userId)).thenReturn(com.joinlivora.backend.presence.model.CreatorAvailabilityStatus.OFFLINE);

        lenient().when(userService.resolveUserFromSubject(principalId)).thenReturn(Optional.of(user));
        lenient().when(creatorRepository.findByUser_Id(userId)).thenReturn(Optional.of(creator));
        
        // Mock that stream is active
        lenient().when(liveStreamService.isStreamActive(userId)).thenReturn(true);
        lenient().when(chatRoomServiceV2.activateWaitingRooms(creatorId)).thenReturn(List.of());

        presenceService.handleWebSocketConnectListener(new org.springframework.web.socket.messaging.SessionConnectEvent(this, connectMsg, principal));

        // Verify that activateWaitingRooms WAS called
        verify(chatRoomServiceV2).activateWaitingRooms(creatorId);
    }
}
