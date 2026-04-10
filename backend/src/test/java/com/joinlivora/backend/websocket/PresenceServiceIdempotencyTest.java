package com.joinlivora.backend.websocket;

import com.joinlivora.backend.auth.event.UserLogoutEvent;
import com.joinlivora.backend.creator.model.Creator;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.creator.service.OnlineStatusService;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.presence.service.OnlineCreatorRegistry;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.livestream.service.LiveStreamService;
import com.joinlivora.backend.streaming.StreamService;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.streaming.CreatorGoLiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PresenceServiceIdempotencyTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private StreamService LiveStreamService;
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
    private com.joinlivora.backend.livestream.service.LiveStreamService liveStreamService;
    @Mock
    private LiveViewerCounterService liveViewerCounterService;
    @Mock
    private StreamAssistantBotService liveStreamAssistantBotService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private org.springframework.data.redis.core.SetOperations<String, Object> setOperations;
    @Mock
    private com.joinlivora.backend.presence.service.RedisSessionRegistryService redisSessionRegistry;

    @Mock
    private com.joinlivora.backend.config.MetricsService metricsService;

    @Mock
    private io.micrometer.core.instrument.Counter metricsCounter;

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        lenient().when(onlineStatusService.isAvailable()).thenReturn(true);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        // Make redisSessionRegistry mock stateful: store/retrieve session data
        java.util.concurrent.ConcurrentHashMap<String, String> principalStore = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.ConcurrentHashMap<String, Long> userIdStore = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.ConcurrentHashMap<String, Long> creatorIdStore = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.ConcurrentHashMap<String, java.util.Set<String>> subsStore = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.ConcurrentHashMap<String, java.util.Set<Long>> streamsStore = new java.util.concurrent.ConcurrentHashMap<>();
        lenient().doAnswer(inv -> { String sid = inv.getArgument(0); String p = inv.getArgument(1); Long u = inv.getArgument(2); Long c = inv.getArgument(3); if (p != null) principalStore.put(sid, p); if (u != null) userIdStore.put(sid, u); if (c != null) creatorIdStore.put(sid, c); return null; }).when(redisSessionRegistry).registerSession(anyString(), any(), any(), any(), any(), any());
        lenient().doAnswer(inv -> { String sid = inv.getArgument(0); principalStore.remove(sid); userIdStore.remove(sid); creatorIdStore.remove(sid); subsStore.remove(sid); streamsStore.remove(sid); return null; }).when(redisSessionRegistry).unregisterSession(anyString());
        lenient().doAnswer(inv -> { String sid = inv.getArgument(0); String dest = inv.getArgument(2); subsStore.computeIfAbsent(sid, k -> java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>())).add(dest); return true; }).when(redisSessionRegistry).addSubscription(anyString(), anyString(), anyString());
        lenient().doAnswer(inv -> streamsStore.computeIfAbsent(inv.getArgument(0), k -> java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>())).add(inv.getArgument(1))).when(redisSessionRegistry).markStreamJoined(anyString(), any());
        lenient().doAnswer(inv -> { java.util.Set<Long> s = streamsStore.get(inv.getArgument(0)); return s != null && s.remove(inv.getArgument(1)); }).when(redisSessionRegistry).markStreamLeft(anyString(), any());
        lenient().when(redisSessionRegistry.getPrincipal(anyString())).thenAnswer(inv -> principalStore.get(inv.getArgument(0)));
        lenient().when(redisSessionRegistry.getUserId(anyString())).thenAnswer(inv -> userIdStore.get(inv.getArgument(0)));
        lenient().when(redisSessionRegistry.getCreatorId(anyString())).thenAnswer(inv -> creatorIdStore.get(inv.getArgument(0)));
        lenient().when(redisSessionRegistry.getIp(anyString())).thenReturn(null);
        lenient().when(redisSessionRegistry.getUserAgent(anyString())).thenReturn(null);
        lenient().when(redisSessionRegistry.getSubscriptions(anyString())).thenAnswer(inv -> subsStore.getOrDefault(inv.getArgument(0), java.util.Collections.emptySet()));
        lenient().when(redisSessionRegistry.isSubscribedTo(anyString(), anyString())).thenAnswer(inv -> { java.util.Set<String> s = subsStore.get(inv.getArgument(0)); return s != null && s.contains(inv.getArgument(1)); });
        lenient().when(redisSessionRegistry.getJoinedStreams(anyString())).thenAnswer(inv -> streamsStore.getOrDefault(inv.getArgument(0), java.util.Collections.emptySet()));
        lenient().when(redisSessionRegistry.getJoinTime(anyString(), anyString())).thenReturn(null);
        lenient().when(redisSessionRegistry.getAllActiveSessions()).thenAnswer(inv -> new java.util.HashMap<>(principalStore));
        lenient().when(redisSessionRegistry.getActiveSessionsCount()).thenAnswer(inv -> (long) principalStore.size());
        
        lenient().when(metricsService.getRedisFailuresTotal()).thenReturn(metricsCounter);
        com.joinlivora.backend.resilience.RedisCircuitBreakerService redisCb =
                new com.joinlivora.backend.resilience.RedisCircuitBreakerService(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        com.joinlivora.backend.presence.service.SessionRegistryService sessionRegistry = new com.joinlivora.backend.presence.service.SessionRegistryService(redisSessionRegistry, metricsService, redisCb);
        com.joinlivora.backend.presence.service.PresenceTrackingService presenceTracking = new com.joinlivora.backend.presence.service.PresenceTrackingService(redisTemplate, onlineStatusService);
        com.joinlivora.backend.presence.service.ViewerCountService viewerCountService = new com.joinlivora.backend.presence.service.ViewerCountService(liveViewerCounterService);
        com.joinlivora.backend.presence.service.PresenceEventOrchestrator eventOrchestrator = new com.joinlivora.backend.presence.service.PresenceEventOrchestrator(
                messagingTemplate, analyticsEventPublisher, liveStreamAssistantBotService, chatRoomServiceV2,
                PresenceService.createAlwaysAvailableBrokerListener(), null);

        presenceService = new PresenceService(
                sessionRegistry,
                presenceTracking,
                viewerCountService,
                eventOrchestrator,
                userService,
                LiveStreamService,
                liveStreamService,
                null,  // creatorProfileService
                onlineCreatorRegistry,
                liveViewerCounterService,
                null,  // creatorFollowRepository
                null   // streamModeratorService
        );
    }

    @Test
    void handleLogoutThenDisconnect_ShouldNotDoubleDecrement() {
        String sessionId = "sess-1";
        Long userId = 1L;
        String principalId = userId.toString();
        String email = "creator@test.com";
        Long creatorId = 10L;
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(principalId);

        // 1. Connect
        Message<byte[]> connectMsg = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeadersMap = new java.util.HashMap<>();
        connectHeadersMap.put("simpSessionId", sessionId);
        when(connectMsg.getHeaders()).thenReturn(new org.springframework.messaging.MessageHeaders(connectHeadersMap));
        
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setRole(Role.CREATOR);
        when(userService.resolveUserFromSubject(principalId)).thenReturn(Optional.of(user));
        when(userService.getByEmail(email)).thenReturn(user);
        when(userService.getById(userId)).thenReturn(user);

        Creator creator = new Creator();
        creator.setId(creatorId);
        creator.setUser(user);
        when(creatorRepository.findByUser_Id(userId)).thenReturn(Optional.of(creator));
        
        when(creatorPresenceService.getAvailability(userId)).thenReturn(com.joinlivora.backend.presence.model.CreatorAvailabilityStatus.OFFLINE);

        presenceService.handleWebSocketConnectListener(new SessionConnectEvent(this, connectMsg, principal));

        // Verify increment
        verify(valueOperations).increment("user_session_count:" + userId);

        // 2. Logout Event
        UserLogoutEvent logoutEvent = new UserLogoutEvent(this, email);
        
        // Mock decrement to return 0 (last session)
        when(valueOperations.decrement("user_session_count:" + userId)).thenReturn(0L);

        presenceService.handleLogout(logoutEvent);

        // Verify decrement happened once
        verify(valueOperations, times(1)).decrement("user_session_count:" + userId);
        verify(creatorPresenceService, times(1)).markOffline(creatorId);

        // 3. Disconnect Event (physical)
        SessionDisconnectEvent disconnectEvent = mock(SessionDisconnectEvent.class);
        when(disconnectEvent.getSessionId()).thenReturn(sessionId);
        
        // Mock principal to resolve userId again via fallback (simulating the bug)
        // StompPrincipal or UserPrincipal
        com.joinlivora.backend.websocket.StompPrincipal stompPrincipal = new com.joinlivora.backend.websocket.StompPrincipal(principalId, String.valueOf(userId));
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth = 
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(stompPrincipal, null);
        when(disconnectEvent.getUser()).thenReturn(auth);

        presenceService.handleWebSocketDisconnectListener(disconnectEvent);

        // VERIFY: Should NOT have decremented a second time
        verify(valueOperations, times(1)).decrement("user_session_count:" + userId);
        // VERIFY: Should NOT have called markOffline a second time
        verify(creatorPresenceService, times(1)).markOffline(creatorId);
    }

    @Test
    void handleStandardDisconnect_ShouldDecrementCorrectly() {
        String sessionId = "sess-2";
        Long userId = 2L;
        String principalId = userId.toString();
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(principalId);

        // 1. Connect
        Message<byte[]> connectMsg = (Message<byte[]>) mock(Message.class);
        Map<String, Object> connectHeadersMap = new java.util.HashMap<>();
        connectHeadersMap.put("simpSessionId", sessionId);
        when(connectMsg.getHeaders()).thenReturn(new org.springframework.messaging.MessageHeaders(connectHeadersMap));
        
        User user = new User();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setRole(Role.USER);
        when(userService.resolveUserFromSubject(principalId)).thenReturn(Optional.of(user));
        when(userService.getById(userId)).thenReturn(user);

        presenceService.handleWebSocketConnectListener(new SessionConnectEvent(this, connectMsg, principal));

        // Verify increment
        verify(valueOperations).increment("user_session_count:" + userId);

        // 2. Disconnect Event
        SessionDisconnectEvent disconnectEvent = mock(SessionDisconnectEvent.class);
        when(disconnectEvent.getSessionId()).thenReturn(sessionId);
        
        // Mock decrement to return 0
        when(valueOperations.decrement("user_session_count:" + userId)).thenReturn(0L);

        presenceService.handleWebSocketDisconnectListener(disconnectEvent);

        // VERIFY: Decrement happened
        verify(valueOperations, times(1)).decrement("user_session_count:" + userId);
    }
}








