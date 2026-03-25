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

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        presenceService = new PresenceService(
                messagingTemplate, streamService, analyticsEventPublisher, userService,
                onlineStatusService, creatorPresenceService, onlineCreatorRegistry, creatorRepository,
                chatRoomRepositoryV2, chatRoomServiceV2, creatorGoLiveService, liveViewerCounterService,
                streamAssistantBotService, redisTemplate, liveStreamService
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
