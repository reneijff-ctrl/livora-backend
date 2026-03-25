package com.joinlivora.backend.websocket;

import com.joinlivora.backend.chat.domain.ChatRoomStatus;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.presence.service.OnlineCreatorRegistry;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.presence.model.CreatorAvailabilityStatus;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRoom;
import com.joinlivora.backend.streaming.StreamService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresenceServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private StreamService LiveStreamService;

    @Mock
    private com.joinlivora.backend.livestream.service.LiveStreamService liveStreamServiceReal;

    @Mock
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @Mock
    private com.joinlivora.backend.user.UserService userService;

    @Mock
    private com.joinlivora.backend.creator.service.OnlineStatusService onlineStatusService;

    @Mock
    private CreatorPresenceService creatorPresenceService;

    @Mock
    private OnlineCreatorRegistry onlineCreatorRegistry;

    @Mock
    private com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository;

    @Mock
    private com.joinlivora.backend.chat.repository.ChatRoomRepository chatRoomRepositoryV2;

    @Mock
    private ChatRoomService chatRoomServiceV2;

    @Mock
    private com.joinlivora.backend.streaming.service.LiveViewerCounterService liveViewerCounterService;

    @Mock
    private com.joinlivora.backend.streaming.service.StreamAssistantBotService liveStreamAssistantBotService;

    @Mock
    private com.joinlivora.backend.streaming.CreatorGoLiveService creatorGoLiveService;

    @Mock
    private com.joinlivora.backend.presence.service.BrokerAvailabilityListener brokerAvailabilityListener;

    @Mock
    private com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;

    private PresenceService presenceService;

    private UUID liveStreamId;
    private String destination;
    private String sessionId = "session-123";
    private String subscriptionId = "sub-456";

    @BeforeEach
    void setUp() {
        lenient().when(onlineStatusService.isAvailable()).thenReturn(true);
        lenient().when(creatorPresenceService.getAvailability(anyLong())).thenReturn(CreatorAvailabilityStatus.ONLINE);
        lenient().when(brokerAvailabilityListener.isBrokerAvailable()).thenReturn(true);
        
        com.joinlivora.backend.creator.model.CreatorProfile creatorProfileMock = new com.joinlivora.backend.creator.model.CreatorProfile();
        creatorProfileMock.setId(777L);
        lenient().when(creatorProfileService.initializeCreatorProfile(any())).thenReturn(creatorProfileMock);
        lenient().when(creatorProfileService.getCreatorIdByUserId(anyLong())).thenReturn(Optional.of(777L));
        lenient().when(liveViewerCounterService.getActiveSessionId(anyLong())).thenReturn(1001L);
        
        com.joinlivora.backend.presence.service.SessionRegistryService sessionRegistry = new com.joinlivora.backend.presence.service.SessionRegistryService();
        com.joinlivora.backend.presence.service.PresenceTrackingService presenceTracking = new com.joinlivora.backend.presence.service.PresenceTrackingService(null, onlineStatusService);
        com.joinlivora.backend.presence.service.ViewerCountService viewerCountService = new com.joinlivora.backend.presence.service.ViewerCountService(liveViewerCounterService);
        com.joinlivora.backend.presence.service.PresenceEventOrchestrator eventOrchestrator = new com.joinlivora.backend.presence.service.PresenceEventOrchestrator(messagingTemplate, analyticsEventPublisher, liveStreamAssistantBotService, chatRoomServiceV2, brokerAvailabilityListener, null);

        presenceService = new PresenceService(
                sessionRegistry,
                presenceTracking,
                viewerCountService,
                eventOrchestrator,
                userService,
                LiveStreamService,
                liveStreamServiceReal,
                creatorProfileService,
                onlineCreatorRegistry,
                liveViewerCounterService,
                null,  // creatorFollowRepository
                null   // streamModeratorService
        );
        liveStreamId = UUID.randomUUID();
        destination = "/exchange/amq.topic/viewers." + liveStreamId;
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleSubscribe_ShouldIncrementViewerCount() {
        Message<byte[]> message = (Message<byte[]>) mock(Message.class);
        Map<String, Object> headers = new HashMap<>();
        headers.put("simpDestination", destination);
        headers.put("simpSessionId", sessionId);
        headers.put("simpSubscriptionId", subscriptionId);
        lenient().when(message.getHeaders()).thenReturn(new MessageHeaders(headers));
        
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, message);
        
        com.joinlivora.backend.user.User creator = new com.joinlivora.backend.user.User();
        creator.setId(1L);
        StreamRoom room = new StreamRoom();
        room.setViewerCount(1);
        room.setCreator(creator);
        lenient().when(LiveStreamService.getRoom(liveStreamId)).thenReturn(room);
        lenient().when(liveViewerCounterService.getActiveSessionId(1L)).thenReturn(1001L);

        presenceService.handleSubscriptionEvent(event);
        verify(liveViewerCounterService).addViewer(eq(1001L), eq(1L), any(), eq(sessionId), any(), any());
        verify(analyticsEventPublisher).publishEvent(eq(com.joinlivora.backend.analytics.AnalyticsEventType.STREAM_JOIN), any(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleMultipleSubscribes_ShouldOnlyIncrementOnce() {
        Message<byte[]> message = (Message<byte[]>) mock(Message.class);
        Map<String, Object> headers = new HashMap<>();
        headers.put("simpDestination", destination);
        headers.put("simpSessionId", sessionId);
        headers.put("simpSubscriptionId", subscriptionId);
        when(message.getHeaders()).thenReturn(new MessageHeaders(headers));
        
        com.joinlivora.backend.user.User creator = new com.joinlivora.backend.user.User();
        creator.setId(1L);
        StreamRoom room = new StreamRoom();
        room.setViewerCount(1);
        room.setCreator(creator);
        when(LiveStreamService.getRoom(liveStreamId)).thenReturn(room);
        when(liveViewerCounterService.getActiveSessionId(1L)).thenReturn(1001L);

        // First subscribe
        presenceService.handleSubscriptionEvent(new SessionSubscribeEvent(this, message));

        // Second subscribe (different subId, same destination)
        Map<String, Object> headers2 = new HashMap<>(headers);
        headers2.put("simpSubscriptionId", "sub-789");
        Message<byte[]> message2 = (Message<byte[]>) mock(Message.class);
        when(message2.getHeaders()).thenReturn(new MessageHeaders(headers2));
        presenceService.handleSubscriptionEvent(new SessionSubscribeEvent(this, message2));

        verify(liveViewerCounterService, times(1)).addViewer(eq(1001L), eq(1L), any(), eq(sessionId), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleUnsubscribe_ShouldDecrementViewerCount() {
        com.joinlivora.backend.user.User creator = new com.joinlivora.backend.user.User();
        creator.setId(1L);

        // Mock liveStreamRoom for join
        StreamRoom room = new StreamRoom();
        room.setViewerCount(1);
        room.setCreator(creator);
        when(LiveStreamService.getRoom(liveStreamId)).thenReturn(room);

        // First subscribe
        Message<byte[]> subMessage = (Message<byte[]>) mock(Message.class);
        Map<String, Object> subHeaders = new HashMap<>();
        subHeaders.put("simpDestination", destination);
        subHeaders.put("simpSessionId", sessionId);
        subHeaders.put("simpSubscriptionId", subscriptionId);
        when(subMessage.getHeaders()).thenReturn(new MessageHeaders(subHeaders));
        presenceService.handleSubscriptionEvent(new SessionSubscribeEvent(this, subMessage));

        // Then unsubscribe
        Message<byte[]> unsubMessage = (Message<byte[]>) mock(Message.class);
        Map<String, Object> unsubHeaders = new HashMap<>();
        unsubHeaders.put("simpSessionId", sessionId);
        unsubHeaders.put("simpSubscriptionId", subscriptionId);
        when(unsubMessage.getHeaders()).thenReturn(new MessageHeaders(unsubHeaders));
        
        SessionUnsubscribeEvent event = new SessionUnsubscribeEvent(this, unsubMessage);
        
        // Mock for leave
        StreamRoom roomAfterLeave = new StreamRoom();
        roomAfterLeave.setViewerCount(0);
        roomAfterLeave.setCreator(creator);
        when(LiveStreamService.getRoom(liveStreamId)).thenReturn(roomAfterLeave);
        when(liveViewerCounterService.getActiveSessionId(1L)).thenReturn(1001L);

        presenceService.handleUnsubscribeEvent(event);
        verify(liveViewerCounterService).removeViewer(eq(1001L), eq(1L), any(), eq(sessionId), any(), any());
        verify(analyticsEventPublisher).publishEvent(eq(com.joinlivora.backend.analytics.AnalyticsEventType.STREAM_LEAVE), any(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleDisconnect_ShouldDecrementViewerCount() {
        com.joinlivora.backend.user.User creator = new com.joinlivora.backend.user.User();
        creator.setId(1L);

        // Mock liveStreamRoom for join
        StreamRoom room = new StreamRoom();
        room.setViewerCount(1);
        room.setCreator(creator);
        when(LiveStreamService.getRoom(liveStreamId)).thenReturn(room);

        // First subscribe
        Message<byte[]> subMessage = (Message<byte[]>) mock(Message.class);
        Map<String, Object> subHeaders = new HashMap<>();
        subHeaders.put("simpDestination", destination);
        subHeaders.put("simpSessionId", sessionId);
        subHeaders.put("simpSubscriptionId", subscriptionId);
        lenient().when(subMessage.getHeaders()).thenReturn(new MessageHeaders(subHeaders));
        presenceService.handleSubscriptionEvent(new SessionSubscribeEvent(this, subMessage));

        // Then disconnect
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        lenient().when(event.getSessionId()).thenReturn(sessionId);
        
        // Mock for leave
        StreamRoom roomAfterLeave = new StreamRoom();
        roomAfterLeave.setViewerCount(0);
        roomAfterLeave.setCreator(creator);
        when(LiveStreamService.getRoom(liveStreamId)).thenReturn(roomAfterLeave);

        presenceService.handleWebSocketDisconnectListener(event);
        verify(liveViewerCounterService).removeViewer(eq(1001L), eq(1L), any(), eq(sessionId), any(), any());
        verify(analyticsEventPublisher).publishEvent(eq(com.joinlivora.backend.analytics.AnalyticsEventType.STREAM_LEAVE), any(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleDisconnect_CreatorOffline_ShouldBroadcastSystemMessage() {
        String creatorEmail = "creatorUserId@test.com";
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(creatorEmail);

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(1L);
        user.setRole(com.joinlivora.backend.user.Role.CREATOR);
        when(userService.getByEmail(creatorEmail)).thenReturn(user);

        // 1. Connect
        Message<byte[]> connectMsg = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeaders = new HashMap<>();
        connectHeaders.put("simpSessionId", sessionId);
        when(connectMsg.getHeaders()).thenReturn(new MessageHeaders(connectHeaders));
        
        SessionConnectEvent connectEvent = new SessionConnectEvent(this, connectMsg, principal);
        presenceService.handleWebSocketConnectListener(connectEvent);

        // 2. Disconnect
        SessionDisconnectEvent disconnectEvent = mock(SessionDisconnectEvent.class);
        when(disconnectEvent.getSessionId()).thenReturn(sessionId);

        StreamRoom room = new StreamRoom();
        room.setId(liveStreamId);
        room.setLive(true);
        room.setCreator(user);
        when(LiveStreamService.findByCreatorId(1L)).thenReturn(Optional.of(room));

        presenceService.handleWebSocketDisconnectListener(disconnectEvent);

        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat/1"), any(RealtimeMessage.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleConnect_Creator_EfficientResolution() {
        String email = "creator@test.com";
        String userIdStr = "123";
        Long userId = 123L;
        Long internalCreatorId = 777L;
        StompPrincipal stompPrincipal = new StompPrincipal(email, userIdStr);
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth = 
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                stompPrincipal, null, java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CREATOR")));

        Message<byte[]> connectMsg = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeaders = new HashMap<>();
        connectHeaders.put("simpSessionId", sessionId);
        when(connectMsg.getHeaders()).thenReturn(new MessageHeaders(connectHeaders));
        
        com.joinlivora.backend.creator.model.Creator creator = com.joinlivora.backend.creator.model.Creator.builder()
                .id(internalCreatorId)
                .user(new com.joinlivora.backend.user.User())
                .build();
        creator.getUser().setId(userId);
        when(creatorRepository.findByUser_Id(userId)).thenReturn(Optional.of(creator));

        SessionConnectEvent connectEvent = new SessionConnectEvent(this, connectMsg, auth);
        presenceService.handleWebSocketConnectListener(connectEvent);

        verify(onlineCreatorRegistry).markOnline(internalCreatorId, sessionId);
        verify(onlineStatusService).setOnline(internalCreatorId);
        // Verify we DID NOT call userService
        verify(userService, never()).getByEmail(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleConnect_Creator_ShouldSetOnline() {
        String email = "creatorUserId@test.com";
        Long userId = 1L;
        Long internalCreatorId = 777L;
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(email);

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setRole(com.joinlivora.backend.user.Role.CREATOR);
        when(userService.getByEmail(email)).thenReturn(user);

        com.joinlivora.backend.creator.model.Creator creator = com.joinlivora.backend.creator.model.Creator.builder()
                .id(internalCreatorId)
                .user(user)
                .build();
        when(creatorRepository.findByUser_Id(userId)).thenReturn(Optional.of(creator));

        Message<byte[]> connectMsg = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeaders = new HashMap<>();
        connectHeaders.put("simpSessionId", sessionId);
        when(connectMsg.getHeaders()).thenReturn(new MessageHeaders(connectHeaders));
        
        SessionConnectEvent connectEvent = new SessionConnectEvent(this, connectMsg, principal);
        presenceService.handleWebSocketConnectListener(connectEvent);

        verify(onlineCreatorRegistry).markOnline(internalCreatorId, sessionId);
        verify(onlineStatusService).setOnline(internalCreatorId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleDisconnect_LastSession_ShouldSetOffline() {
        String email = "creatorUserId@test.com";
        Long userId = 1L;
        Long internalCreatorId = 777L;
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(email);

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setRole(com.joinlivora.backend.user.Role.CREATOR);
        when(userService.getByEmail(email)).thenReturn(user);

        com.joinlivora.backend.creator.model.Creator creator = com.joinlivora.backend.creator.model.Creator.builder()
                .id(internalCreatorId)
                .user(user)
                .build();
        when(creatorRepository.findByUser_Id(userId)).thenReturn(Optional.of(creator));

        Message<byte[]> connectMsg = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeaders = new HashMap<>();
        connectHeaders.put("simpSessionId", sessionId);
        when(connectMsg.getHeaders()).thenReturn(new MessageHeaders(connectHeaders));
        
        SessionConnectEvent connectEvent = new SessionConnectEvent(this, connectMsg, principal);
        presenceService.handleWebSocketConnectListener(connectEvent);

        // Disconnect
        SessionDisconnectEvent disconnectEvent = mock(SessionDisconnectEvent.class);
        when(disconnectEvent.getSessionId()).thenReturn(sessionId);
        
        presenceService.handleWebSocketDisconnectListener(disconnectEvent);

        verify(onlineCreatorRegistry).markOffline(internalCreatorId);
        verify(onlineStatusService).setOffline(internalCreatorId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleDisconnect_NotLastSession_ShouldNotSetOffline() {
        String email = "creatorUserId@test.com";
        Long userId = 1L;
        Long internalCreatorId = 777L;
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(email);

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setRole(com.joinlivora.backend.user.Role.CREATOR);
        when(userService.getByEmail(email)).thenReturn(user);

        com.joinlivora.backend.creator.model.Creator creator = com.joinlivora.backend.creator.model.Creator.builder()
                .id(internalCreatorId)
                .user(user)
                .build();
        when(creatorRepository.findByUser_Id(userId)).thenReturn(Optional.of(creator));

        // Session 1
        Message<byte[]> connectMsg1 = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeaders1 = new HashMap<>();
        connectHeaders1.put("simpSessionId", "session-1");
        when(connectMsg1.getHeaders()).thenReturn(new MessageHeaders(connectHeaders1));
        presenceService.handleWebSocketConnectListener(new SessionConnectEvent(this, connectMsg1, principal));

        // Session 2
        Message<byte[]> connectMsg2 = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeaders2 = new HashMap<>();
        connectHeaders2.put("simpSessionId", "session-2");
        when(connectMsg2.getHeaders()).thenReturn(new MessageHeaders(connectHeaders2));
        presenceService.handleWebSocketConnectListener(new SessionConnectEvent(this, connectMsg2, principal));

        // Disconnect Session 1
        SessionDisconnectEvent disconnectEvent = mock(SessionDisconnectEvent.class);
        when(disconnectEvent.getSessionId()).thenReturn("session-1");
        
        presenceService.handleWebSocketDisconnectListener(disconnectEvent);

        verify(onlineStatusService, never()).setOffline(internalCreatorId);

        // Disconnect Session 2
        SessionDisconnectEvent disconnectEvent2 = mock(SessionDisconnectEvent.class);
        when(disconnectEvent2.getSessionId()).thenReturn("session-2");
        
        presenceService.handleWebSocketDisconnectListener(disconnectEvent2);

        verify(onlineStatusService).setOffline(internalCreatorId);
        verify(onlineCreatorRegistry).markOffline(internalCreatorId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleDisconnect_RegularUser_ShouldNotUpdateCreatorPresence() {
        String email = "regular@test.com";
        Long userId = 2L;
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(email);

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setRole(com.joinlivora.backend.user.Role.USER);
        when(userService.getByEmail(email)).thenReturn(user);

        Message<byte[]> connectMsg = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeaders = new HashMap<>();
        connectHeaders.put("simpSessionId", sessionId);
        when(connectMsg.getHeaders()).thenReturn(new MessageHeaders(connectHeaders));
        
        SessionConnectEvent connectEvent = new SessionConnectEvent(this, connectMsg, principal);
        presenceService.handleWebSocketConnectListener(connectEvent);

        // Verify no online update for regular user
        verify(creatorPresenceService, never()).markOnline(anyLong());

        // Disconnect
        SessionDisconnectEvent disconnectEvent = mock(SessionDisconnectEvent.class);
        when(disconnectEvent.getSessionId()).thenReturn(sessionId);
        
        presenceService.handleWebSocketDisconnectListener(disconnectEvent);

        // This is what we want to ensure: no updatePresence(userId, false) for regular users
        verify(creatorPresenceService, never()).markOffline(userId);
        verify(onlineStatusService, never()).setOffline(userId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleDisconnect_NotLastSession_ShouldNotBroadcastSystemMessage() {
        String email = "creatorUserId@test.com";
        Long userId = 1L;
        Long internalCreatorId = 777L;
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(email);

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setRole(com.joinlivora.backend.user.Role.CREATOR);
        when(userService.getByEmail(email)).thenReturn(user);

        com.joinlivora.backend.creator.model.Creator creator = com.joinlivora.backend.creator.model.Creator.builder()
                .id(internalCreatorId)
                .user(user)
                .build();
        when(creatorRepository.findByUser_Id(userId)).thenReturn(Optional.of(creator));

        // Session 1
        Message<byte[]> connectMsg1 = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeaders1 = new HashMap<>();
        connectHeaders1.put("simpSessionId", "session-1");
        when(connectMsg1.getHeaders()).thenReturn(new MessageHeaders(connectHeaders1));
        presenceService.handleWebSocketConnectListener(new SessionConnectEvent(this, connectMsg1, principal));

        // Session 2
        Message<byte[]> connectMsg2 = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeaders2 = new HashMap<>();
        connectHeaders2.put("simpSessionId", "session-2");
        when(connectMsg2.getHeaders()).thenReturn(new MessageHeaders(connectHeaders2));
        presenceService.handleWebSocketConnectListener(new SessionConnectEvent(this, connectMsg2, principal));

        // Disconnect Session 1
        SessionDisconnectEvent disconnectEvent = mock(SessionDisconnectEvent.class);
        when(disconnectEvent.getSessionId()).thenReturn("session-1");
        
        StreamRoom room = new StreamRoom();
        room.setId(liveStreamId);
        room.setLive(true);
        room.setCreator(user);
        lenient().when(LiveStreamService.findByCreatorId(1L)).thenReturn(Optional.of(room));

        presenceService.handleWebSocketDisconnectListener(disconnectEvent);

        // Should NOT broadcast system message yet
        verify(messagingTemplate, never()).convertAndSend(eq("/exchange/amq.topic/chat." + user.getId()), any(RealtimeMessage.class));

        // Disconnect Session 2
        SessionDisconnectEvent disconnectEvent2 = mock(SessionDisconnectEvent.class);
        when(disconnectEvent2.getSessionId()).thenReturn("session-2");
        
        presenceService.handleWebSocketDisconnectListener(disconnectEvent2);

        // NOW it should broadcast
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + user.getId()), any(RealtimeMessage.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenRedisUnavailable_ShouldStillWorkLocalOnly() {
        // Create a new presence service with redis disabled
        com.joinlivora.backend.creator.service.OnlineStatusService offlineStatusService = mock(com.joinlivora.backend.creator.service.OnlineStatusService.class);
        when(offlineStatusService.isAvailable()).thenReturn(false);
        
        PresenceService localOnlyPresenceService = new PresenceService(
                messagingTemplate, LiveStreamService, analyticsEventPublisher, userService, offlineStatusService, creatorPresenceService, onlineCreatorRegistry, creatorRepository, chatRoomRepositoryV2, chatRoomServiceV2);

        // Try to connect
        Message<byte[]> connectMsg = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeaders = new HashMap<>();
        connectHeaders.put("simpSessionId", sessionId);
        lenient().when(connectMsg.getHeaders()).thenReturn(new MessageHeaders(connectHeaders));
        Principal principal = mock(Principal.class);
        lenient().when(principal.getName()).thenReturn("test@test.com");

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(1L);
        user.setRole(com.joinlivora.backend.user.Role.USER);
        when(userService.getByEmail("test@test.com")).thenReturn(user);

        localOnlyPresenceService.handleWebSocketConnectListener(new SessionConnectEvent(this, connectMsg, principal));

        // Verify it still calls userService and broadcasts presence
        verify(userService).getByEmail("test@test.com");
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/presence"), anyMap());
        
        // BUT verify NO interactions with Redis part of onlineStatusService
        verify(offlineStatusService, never()).setOnline(anyLong());
    }
    @Test
    @SuppressWarnings("unchecked")
    void handleConnect_CreatorDoesNotImplicitlyActivateWaitingRoom() {
        // Arrange: creator identified via StompPrincipal with ROLE_CREATOR
        String email = "creator@test.com";
        String userId = "123";
        StompPrincipal stompPrincipal = new StompPrincipal(email, userId);
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        stompPrincipal, null, java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CREATOR")));

        // Session headers
        Message<byte[]> connectMsg = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeaders = new HashMap<>();
        connectHeaders.put("simpSessionId", sessionId);
        when(connectMsg.getHeaders()).thenReturn(new MessageHeaders(connectHeaders));

        // Mock creatorRepository mapping from userId -> Creator.id
        com.joinlivora.backend.creator.model.Creator creator = com.joinlivora.backend.creator.model.Creator.builder()
                .id(777L)
                .user(new com.joinlivora.backend.user.User())
                .build();
        creator.getUser().setId(Long.valueOf(userId));
        when(creatorRepository.findByUser_Id(123L)).thenReturn(java.util.Optional.of(creator));

        // Act: connect event
        SessionConnectEvent connectEvent = new SessionConnectEvent(this, connectMsg, auth);
        presenceService.handleWebSocketConnectListener(connectEvent);

        // Assert: service NOT called (sessions must be explicitly started)
        verify(chatRoomServiceV2, never()).activateWaitingRooms(anyLong());
        verify(messagingTemplate, never()).convertAndSend(contains("/status"), any(com.joinlivora.backend.websocket.RealtimeMessage.class));
    }

}








