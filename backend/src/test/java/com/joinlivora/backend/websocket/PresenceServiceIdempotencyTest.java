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

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        lenient().when(onlineStatusService.isAvailable()).thenReturn(true);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        
        presenceService = new PresenceService(
                messagingTemplate, LiveStreamService, analyticsEventPublisher, userService,
                onlineStatusService, creatorPresenceService, onlineCreatorRegistry,
                creatorRepository, chatRoomRepositoryV2, chatRoomServiceV2,
                creatorGoLiveService, liveViewerCounterService, liveStreamAssistantBotService,
                redisTemplate, liveStreamService
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








