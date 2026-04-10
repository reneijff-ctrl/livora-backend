package com.joinlivora.backend.websocket;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.livestream.service.LiveStreamService;
import com.joinlivora.backend.streaming.StreamService;
import com.joinlivora.backend.creator.service.OnlineStatusService;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.presence.service.OnlineCreatorRegistry;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.streaming.CreatorGoLiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceRiskTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ZSetOperations<String, Object> zSetOperations;
    @Mock
    private UserService userService;
    @Mock
    private StreamService streamService;
    @Mock
    private LiveViewerCounterService liveViewerCounterService;
    @Mock
    private StreamAssistantBotService streamAssistantBotService;
    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
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
    private com.joinlivora.backend.presence.service.RedisSessionRegistryService redisSessionRegistry;

    @Mock
    private com.joinlivora.backend.config.MetricsService metricsService;

    @Mock
    private io.micrometer.core.instrument.Counter metricsCounter;

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
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
                messagingTemplate, analyticsEventPublisher, streamAssistantBotService, chatRoomServiceV2,
                PresenceService.createAlwaysAvailableBrokerListener(), null);

        presenceService = new PresenceService(
                sessionRegistry,
                presenceTracking,
                viewerCountService,
                eventOrchestrator,
                userService,
                streamService,
                liveStreamService,
                null,  // creatorProfileService
                onlineCreatorRegistry,
                liveViewerCounterService,
                null,  // creatorFollowRepository
                null   // streamModeratorService
        );
    }

    @Test
    void handleJoinStream_ShouldTrackNewAccountInCluster() {
        String streamId = UUID.randomUUID().toString();
        String destination = "/exchange/amq.topic/viewers." + streamId;
        String userIdStr = "999";
        String sessionId = "session-123";
        
        User newUser = new User();
        newUser.setId(999L);
        newUser.setEmail("newuser@test.com");
        newUser.setCreatedAt(Instant.now().minus(Duration.ofHours(1))); // < 24h old
        
        User creator = new User();
        creator.setId(111L);
        com.joinlivora.backend.streaming.StreamRoom room = new com.joinlivora.backend.streaming.StreamRoom();
        room.setCreator(creator);

        when(userService.resolveUserFromSubject(userIdStr)).thenReturn(Optional.of(newUser));
        when(streamService.getRoom(any())).thenReturn(room);

        // Mock SessionSubscribeEvent
        org.springframework.messaging.Message<byte[]> message = mock(org.springframework.messaging.Message.class);
        Map<String, Object> headers = new HashMap<>();
        headers.put("simpDestination", destination);
        headers.put("simpSessionId", sessionId);
        headers.put("simpSubscriptionId", "sub-1");
        headers.put("nativeHeaders", new HashMap<>());
        
        // Mock StompPrincipal
        com.joinlivora.backend.websocket.StompPrincipal principal = new com.joinlivora.backend.websocket.StompPrincipal(userIdStr, userIdStr);
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth = 
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null);
        
        when(message.getHeaders()).thenReturn(new org.springframework.messaging.MessageHeaders(headers));
        org.springframework.web.socket.messaging.SessionSubscribeEvent event = 
            new org.springframework.web.socket.messaging.SessionSubscribeEvent(this, message, auth);

        presenceService.handleSubscriptionEvent(event);

        verify(zSetOperations).add(contains("presence:join-cluster:"), eq(userIdStr), anyDouble());
        verify(redisTemplate).expire(contains("presence:join-cluster:"), eq(Duration.ofMinutes(15)));
    }

    @Test
    void getRecentNewAccountJoinCount_ShouldQueryRedis() {
        Long creatorId = 123L;
        Duration duration = Duration.ofMinutes(10);
        when(zSetOperations.zCard(anyString())).thenReturn(5L);

        long count = presenceService.getRecentNewAccountJoinCount(creatorId, duration);

        assertEquals(5, count);
        verify(zSetOperations).removeRangeByScore(eq("presence:join-cluster:" + creatorId), anyDouble(), anyDouble());
        verify(zSetOperations).zCard(eq("presence:join-cluster:" + creatorId));
    }
}
