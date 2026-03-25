package com.joinlivora.backend.streaming;

import com.joinlivora.backend.monetization.dto.SuperTipRequest;
import com.joinlivora.backend.monetization.dto.SuperTipErrorCode;
import com.joinlivora.backend.exception.SuperTipException;
import com.joinlivora.backend.exception.ChatAccessException;
import com.joinlivora.backend.chat.dto.ChatErrorCode;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.chat.ChatTipService;
import com.joinlivora.backend.streaming.service.StreamModerationService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

import com.joinlivora.backend.chat.dto.ModerateResult;
import org.mockito.ArgumentCaptor;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreamChatControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private ChatTipService chatTipService;

    @Mock
    private com.joinlivora.backend.chat.SlowModeBypassService slowModeBypassService;

    @Mock
    private com.joinlivora.backend.chat.ChatRateLimitService chatRateLimitService;

    @Mock
    private StreamService streamService;

    @Mock
    private com.joinlivora.backend.token.TokenService tokenService;

    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.joinlivora.backend.chat.ChatModerationService moderationService;

    @Mock
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @Mock
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;

    @Mock
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;

    @Mock
    private StreamModerationService liveStreamModerationService;

    @Mock
    private com.joinlivora.backend.streaming.service.StreamAssistantBotService liveStreamAssistantBotService;

    @InjectMocks
    private StreamChatController liveStreamChatController;

    private User user;
    private UUID liveStreamId;
    private Principal principal;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");

        liveStreamId = UUID.randomUUID();
        principal = mock(Principal.class);

        lenient().when(restrictionService.getActiveRestriction(any())).thenReturn(java.util.Optional.empty());
        
        User creator = new User();
        creator.setId(2L);
        creator.setEmail("creator2@test.com");
        StreamRoom room = new StreamRoom();
        room.setId(liveStreamId);
        room.setCreator(creator);
        lenient().when(streamService.getRoom(any())).thenReturn(room);
        lenient().when(liveStreamModerationService.isMuted(anyLong(), anyLong())).thenReturn(false);
        lenient().when(liveStreamModerationService.isShadowMuted(anyLong(), anyLong())).thenReturn(false);
        lenient().when(moderationService.moderate(anyString(), anyLong(), any())).thenReturn(ModerateResult.allowed());
    }

    private SimpMessageHeaderAccessor createMockAccessor() {
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        java.util.Map<String, Object> attrs = new java.util.HashMap<>();
        attrs.put("ipAddress", "127.0.0.1");
        lenient().when(accessor.getSessionAttributes()).thenReturn(attrs);
        return accessor;
    }


    @Test
    void handleChatTip_Authenticated_ShouldSucceed() {
        when(principal.getName()).thenReturn("creator@test.com");
        StreamChatController.ChatTipRequest request = new StreamChatController.ChatTipRequest(100L, "Great liveStream!", "req-123");
        when(userService.getByEmail("creator@test.com")).thenReturn(user);

        liveStreamChatController.handleChatTip(liveStreamId, request, principal, createMockAccessor());

        verify(chatRoomService).validateAccess("liveStream-" + liveStreamId, user.getId());
        verify(chatTipService).processChatTip(eq(user), eq(liveStreamId), eq(100L), eq("Great liveStream!"), eq("req-123"), any(), any());
        verify(abuseDetectionService).checkRapidTipping(eq(new java.util.UUID(0L, user.getId())), any());
    }

    @Test
    void handleChatTip_Unauthenticated_ShouldThrowException() {
        StreamChatController.ChatTipRequest request = new StreamChatController.ChatTipRequest(100L, "Great liveStream!", "req-123");

        assertThrows(AccessDeniedException.class, () -> 
            liveStreamChatController.handleChatTip(liveStreamId, request, null, createMockAccessor())
        );

        verifyNoInteractions(userService, chatRoomService, chatTipService);
    }

    @Test
    void handleChatTip_AccessDenied_ShouldPropagateException() {
        when(principal.getName()).thenReturn("creator@test.com");
        StreamChatController.ChatTipRequest request = new StreamChatController.ChatTipRequest(100L, "Great liveStream!", "req-123");
        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        doThrow(new AccessDeniedException("PPV required"))
                .when(chatRoomService).validateAccess(anyString(), anyLong());

        assertThrows(AccessDeniedException.class, () -> 
            liveStreamChatController.handleChatTip(liveStreamId, request, principal, createMockAccessor())
        );

        verify(chatTipService, never()).processChatTip(any(), any(), anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void handleSuperTip_Authenticated_ShouldSucceed() {
        when(principal.getName()).thenReturn("creator@test.com");
        SuperTipRequest request = new SuperTipRequest(5000L, "Amazing!", "st-123");
        when(userService.getByEmail("creator@test.com")).thenReturn(user);

        liveStreamChatController.handleSuperTip(liveStreamId, request, principal, createMockAccessor());

        verify(chatRoomService).validateAccess("liveStream-" + liveStreamId, user.getId());
        verify(chatTipService).processSuperTip(eq(user), eq(liveStreamId), eq(5000L), eq("Amazing!"), eq("st-123"), any(), any());
        verify(abuseDetectionService).checkRapidTipping(eq(new java.util.UUID(0L, user.getId())), any());
    }

    @Test
    void handleSuperTip_Unauthenticated_ShouldThrowException() {
        SuperTipRequest request = new SuperTipRequest(5000L, "Amazing!", "st-123");

        assertThrows(AccessDeniedException.class, () ->
                liveStreamChatController.handleSuperTip(liveStreamId, request, null, createMockAccessor())
        );

        verifyNoInteractions(userService, chatRoomService, chatTipService);
    }

    @Test
    void handleException_ShouldReturnErrorMap() {
        RuntimeException ex = new RuntimeException("Test error");
        java.util.Map<String, Object> result = liveStreamChatController.handleException(ex);

        org.junit.jupiter.api.Assertions.assertEquals("RuntimeException", result.get("error"));
        org.junit.jupiter.api.Assertions.assertEquals("", result.get("errorCode"));
        org.junit.jupiter.api.Assertions.assertEquals("Test error", result.get("message"));
        org.junit.jupiter.api.Assertions.assertNotNull(result.get("timestamp"));
    }

    @Test
    void handleException_SuperTipException_ShouldReturnErrorCode() {
        SuperTipException ex = new SuperTipException(com.joinlivora.backend.monetization.dto.SuperTipErrorCode.INSUFFICIENT_BALANCE, "Insufficient tokens");
        java.util.Map<String, Object> result = liveStreamChatController.handleException(ex);

        org.junit.jupiter.api.Assertions.assertEquals("SuperTipException", result.get("error"));
        org.junit.jupiter.api.Assertions.assertEquals("INSUFFICIENT_BALANCE", result.get("errorCode"));
        org.junit.jupiter.api.Assertions.assertEquals("Insufficient tokens", result.get("message"));
    }


    @Test
    void handleChatTip_TipCooldown_ShouldThrowExceptionWithExpiresAt() {
        when(principal.getName()).thenReturn("creator@test.com");
        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        
        java.time.Instant expiresAt = java.time.Instant.now().plusSeconds(300);
        com.joinlivora.backend.abuse.model.UserRestriction restriction = com.joinlivora.backend.abuse.model.UserRestriction.builder()
                .restrictionLevel(com.joinlivora.backend.abuse.model.RestrictionLevel.TIP_COOLDOWN)
                .expiresAt(expiresAt)
                .build();
        when(restrictionService.getActiveRestriction(any())).thenReturn(java.util.Optional.of(restriction));

        StreamChatController.ChatTipRequest request = new StreamChatController.ChatTipRequest(100L, "Tip", "req-1");

        com.joinlivora.backend.exception.UserRestrictedException ex = assertThrows(com.joinlivora.backend.exception.UserRestrictedException.class, () -> 
            liveStreamChatController.handleChatTip(liveStreamId, request, principal, createMockAccessor())
        );
        
        assertEquals(com.joinlivora.backend.abuse.model.RestrictionLevel.TIP_COOLDOWN, ex.getLevel());
        assertEquals(expiresAt, ex.getExpiresAt());
        verify(chatTipService, never()).processChatTip(any(), any(), anyLong(), anyString(), anyString(), any(), any());
    }


    @Test
    void handleChatTip_ModerationBanned_ShouldThrowException() {
        when(principal.getName()).thenReturn("creator@test.com");
        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(moderationService.isBanned(eq(user.getId()), anyString())).thenReturn(true);

        StreamChatController.ChatTipRequest request = new StreamChatController.ChatTipRequest(100L, "Tip", "req-1");

        assertThrows(AccessDeniedException.class, () -> 
            liveStreamChatController.handleChatTip(liveStreamId, request, principal, createMockAccessor())
        );
    }

}








