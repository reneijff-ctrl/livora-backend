package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.chat.dto.ModerateResult;
import com.joinlivora.backend.chat.dto.ModerationSeverity;
import com.joinlivora.backend.fraud.model.VelocityActionType;
import com.joinlivora.backend.fraud.service.VelocityTrackerService;
import com.joinlivora.backend.streaming.service.StreamModerationService;
import com.joinlivora.backend.websocket.RealtimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private VelocityTrackerService velocityTrackerService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.joinlivora.backend.user.UserRepository userRepository;

    @Mock
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;

    @Mock
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @Mock
    private ChatModerationService moderationService;

    @Mock
    private ChatRateLimiterService chatRateLimiterService;

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private com.joinlivora.backend.chat.service.ChatRoomService chatRoomService;

    @Mock
    private com.joinlivora.backend.chat.repository.ChatRoomRepository chatRoomRepositoryV2;
    @Mock
    private com.joinlivora.backend.chat.repository.ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatViolationLogRepository violationLogRepository;

    @Mock
    private StreamModerationService liveStreamModerationService;

    @Mock
    private com.joinlivora.backend.streaming.service.StreamAssistantBotService liveStreamAssistantBotService;

    @Mock
    private com.joinlivora.backend.streaming.StreamRepository StreamRepository;

    @Mock
    private com.joinlivora.backend.token.TokenService tokenService;

    @Mock
    private com.joinlivora.backend.payout.CreatorEarningsService creatorEarningsService;

    @Mock
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;

    @Mock
    private com.joinlivora.backend.chat.SlowModeBypassService slowModeBypassService;

    @Mock
    private com.joinlivora.backend.chat.ChatRateLimitService chatRateLimitService;

    @Mock
    private com.joinlivora.backend.monetization.HighlightedMessageService highlightedMessageService;

    @Mock
    private com.joinlivora.backend.streaming.StreamService LiveStreamService;

    @Mock
    private com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository;

    @Mock
    private com.joinlivora.backend.streaming.chat.commands.ChatCommandRegistry commandRegistry;

    @Mock
    private com.joinlivora.backend.websocket.PresenceService presenceService;

    @Mock
    private ChatPersistenceService chatPersistenceService;

    @Mock
    private ChatBatchService chatBatchService;

    @InjectMocks
    private ChatMessageService chatMessageService;

    private void stubRoomAndCreator(UUID roomId, Long creatorEntityId, Long creatorUserId) {
        com.joinlivora.backend.chat.domain.ChatRoom v2Room = new com.joinlivora.backend.chat.domain.ChatRoom();
        v2Room.setId(roomId.getLeastSignificantBits());
        v2Room.setCreatorId(creatorEntityId);
        v2Room.setStatus(com.joinlivora.backend.chat.domain.ChatRoomStatus.ACTIVE);
        lenient().when(chatRoomService.getRoomEntity(roomId)).thenReturn(v2Room);
        lenient().when(chatRoomService.getOrCreateRoom(creatorUserId)).thenReturn(v2Room);

        com.joinlivora.backend.creator.model.Creator creator = mock(com.joinlivora.backend.creator.model.Creator.class);
        com.joinlivora.backend.user.User creatorUser = new com.joinlivora.backend.user.User();
        creatorUser.setId(creatorUserId);
        lenient().when(creator.getUser()).thenReturn(creatorUser);
        lenient().when(creatorRepository.findById(creatorEntityId)).thenReturn(java.util.Optional.of(creator));
    }

    @Test
    void processMessage_WhenPositiveSentiment_ShouldSetHighlight() {
        // Arrange
        UUID roomId = UUID.randomUUID();
        Long creatorId = 456L;
        ChatMessageDto messageDto = ChatMessageDto.builder()
                .roomId(roomId)
                .creatorUserId(creatorId)
                .content("Great liveStream!")
                .build();

        ModerateResult positiveResult = ModerateResult.builder()
                .allowed(true)
                .positive(true)
                .build();
        when(moderationService.moderate(anyString(), anyLong(), any())).thenReturn(positiveResult);
        when(chatRateLimiterService.isAllowed(anyLong())).thenReturn(true);
        stubRoomAndCreator(roomId, creatorId, creatorId);

        // Act
        chatMessageService.processMessage(123L, messageDto);

        // Assert
        assertEquals("POSITIVE", messageDto.getHighlight());
        verify(liveStreamAssistantBotService).onPositiveMessage(eq(creatorId), any(), eq("Great liveStream!"));
        verify(chatBatchService).enqueueMessage(eq(creatorId), eq(messageDto));
    }

    @Test
    void processMessage_ShouldTrackVelocityAndBroadcast() {
        Long userId = 123L;
        Long creatorId = 456L;
        UUID roomId = UUID.randomUUID();
        ChatMessageDto chatMessage = new ChatMessageDto();
        chatMessage.setRoomId(roomId);
        chatMessage.setCreatorUserId(creatorId);
        chatMessage.setSenderUsername("creator@test.com");
        chatMessage.setContent("Hello");

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setEmail("creator@test.com");
        user.setStatus(com.joinlivora.backend.user.UserStatus.ACTIVE);
        user.setRole(com.joinlivora.backend.user.Role.USER);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(moderationService.moderate(anyString(), anyLong(), any())).thenReturn(ModerateResult.allowed());
        when(chatRateLimiterService.isAllowed(anyLong())).thenReturn(true);
        stubRoomAndCreator(roomId, creatorId, creatorId);

        chatMessageService.processMessage(userId, chatMessage, "127.0.0.1");

        // Verify abuse detection
        verify(abuseDetectionService).checkMessageSpam(eq(new UUID(0L, userId)), eq("127.0.0.1"));

        // Verify velocity tracking happens
        verify(velocityTrackerService).trackAction(eq(userId), eq(VelocityActionType.MESSAGE));

        // Verify analytics event
        verify(analyticsEventPublisher).publishEvent(eq(com.joinlivora.backend.analytics.AnalyticsEventType.CHAT_MESSAGE_SENT), eq(user), anyMap());

        // Verify broadcast happens
        verify(chatBatchService).enqueueMessage(eq(creatorId), eq(chatMessage));

        // Verify message was finalized
        assertNotNull(chatMessage.getTimestamp());
        assertFalse(chatMessage.isSystemMessage());
    }

    @Test
    void processMessage_WhenBlockedByModeration_ShouldNotBroadcastAndNotifyUser() {
        Long userId = 123L;
        Long creatorId = 456L;
        UUID roomId = UUID.randomUUID();
        ChatMessageDto chatMessage = new ChatMessageDto();
        chatMessage.setRoomId(roomId);
        chatMessage.setCreatorUserId(creatorId);
        chatMessage.setContent("BAD WORD");

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setStatus(com.joinlivora.backend.user.UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));

        ModerateResult blockedResult = ModerateResult.blocked("Prohibited content", ModerationSeverity.MEDIUM);
        when(moderationService.moderate(eq("BAD WORD"), eq(userId), eq(creatorId))).thenReturn(blockedResult);
        stubRoomAndCreator(roomId, creatorId, creatorId);

        chatMessageService.processMessage(userId, chatMessage, "127.0.0.1");

        // Verify broadcast NEVER happens
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(ChatMessageDto.class));

        // Verify violation is logged
        verify(violationLogRepository).save(argThat(log ->
            log.getUserId().equals(userId) &&
            log.getMessage().equals("BAD WORD") &&
            log.getSeverity() == ModerationSeverity.MEDIUM &&
            log.getCreatorId().equals(creatorId)
        ));

        // Verify user IS notified
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), eq("/queue/moderation"), any(RealtimeMessage.class));
    }

    @Test
    void processMessage_WhenHighSeverityModeration_ShouldLogIncidentAndNotifyUser() {
        Long userId = 123L;
        Long creatorId = 456L;
        UUID roomId = UUID.randomUUID();
        ChatMessageDto chatMessage = new ChatMessageDto();
        chatMessage.setRoomId(roomId);
        chatMessage.setCreatorUserId(creatorId);
        chatMessage.setContent("EXTREME CONTENT");

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setStatus(com.joinlivora.backend.user.UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));

        ModerateResult blockedResult = ModerateResult.blocked("Very prohibited content", ModerationSeverity.HIGH);
        when(moderationService.moderate(eq("EXTREME CONTENT"), eq(userId), eq(creatorId))).thenReturn(blockedResult);
        stubRoomAndCreator(roomId, creatorId, creatorId);

        chatMessageService.processMessage(userId, chatMessage, "127.0.0.1");

        // Verify broadcast NEVER happens
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(ChatMessageDto.class));

        // Verify user IS notified
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), eq("/queue/moderation"), any(RealtimeMessage.class));
    }

    @Test
    void processMessage_WhenRateLimited_ShouldNotBroadcastAndNotifyUser() {
        Long userId = 123L;
        Long creatorId = 456L;
        UUID roomId = UUID.randomUUID();
        ChatMessageDto chatMessage = new ChatMessageDto();
        chatMessage.setRoomId(roomId);
        chatMessage.setCreatorUserId(creatorId);
        chatMessage.setContent("Hello fast");

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setStatus(com.joinlivora.backend.user.UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));

        when(moderationService.moderate(anyString(), anyLong(), anyLong())).thenReturn(ModerateResult.allowed());
        when(chatRateLimiterService.isAllowed(userId)).thenReturn(false);
        stubRoomAndCreator(roomId, creatorId, creatorId);

        chatMessageService.processMessage(userId, chatMessage, "127.0.0.1");

        // Verify broadcast NEVER happens
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(ChatMessageDto.class));

        // Verify user IS notified
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), eq("/queue/moderation"), argThat(msg ->
            ((RealtimeMessage)msg).getType().equals("RATE_LIMIT_EXCEEDED")
        ));
    }

    @Test
    void processMessage_WhenShadowMuted_ShouldOnlyBroadcastToSender() {
        Long userId = 123L;
        Long creatorId = 456L;
        UUID roomId = UUID.randomUUID();
        ChatMessageDto chatMessage = new ChatMessageDto();
        chatMessage.setRoomId(roomId);
        chatMessage.setCreatorUserId(creatorId);
        chatMessage.setSenderUsername("user@test.com");
        chatMessage.setContent("Secret message");

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setStatus(com.joinlivora.backend.user.UserStatus.ACTIVE);
        user.setRole(com.joinlivora.backend.user.Role.USER);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));

        when(moderationService.moderate(anyString(), anyLong(), anyLong())).thenReturn(ModerateResult.allowed());
        when(chatRateLimiterService.isAllowed(userId)).thenReturn(true);
        when(moderationService.isShadowMuted(eq(userId), anyString())).thenReturn(true);
        stubRoomAndCreator(roomId, creatorId, creatorId);

        chatMessageService.processMessage(userId, chatMessage, "127.0.0.1");

        // Verify public broadcast NEVER happens
        verify(messagingTemplate, never()).convertAndSend(startsWith("/exchange/amq.topic/chat."), any(ChatMessageDto.class));

        // Verify user RECEIVES their own message via private queue
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), eq("/queue/chat"), eq(chatMessage));
    }

    @Test
    void processMessage_WhenMutedInRedis_ShouldNotBroadcast() {
        Long userId = 123L;
        Long creatorId = 456L;
        UUID roomId = UUID.randomUUID();
        ChatMessageDto chatMessage = new ChatMessageDto();
        chatMessage.setRoomId(roomId);
        chatMessage.setCreatorUserId(creatorId);
        chatMessage.setContent("Hello");

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setStatus(com.joinlivora.backend.user.UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));

        when(moderationService.moderate(anyString(), anyLong(), anyLong())).thenReturn(ModerateResult.allowed());
        when(chatRateLimiterService.isAllowed(anyLong())).thenReturn(true);
        when(liveStreamModerationService.isMuted(creatorId, userId)).thenReturn(true);
        stubRoomAndCreator(roomId, creatorId, creatorId);

        chatMessageService.processMessage(userId, chatMessage, "127.0.0.1");

        // Verify broadcast NEVER happens
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void processMessage_WhenShadowMutedInRedis_ShouldOnlyBroadcastToSender() {
        Long userId = 123L;
        Long creatorId = 456L;
        UUID roomId = UUID.randomUUID();
        ChatMessageDto chatMessage = new ChatMessageDto();
        chatMessage.setRoomId(roomId);
        chatMessage.setCreatorUserId(creatorId);
        chatMessage.setSenderUsername("user@test.com");
        chatMessage.setContent("Secret");

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setStatus(com.joinlivora.backend.user.UserStatus.ACTIVE);
        user.setRole(com.joinlivora.backend.user.Role.USER);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));

        when(moderationService.moderate(anyString(), anyLong(), anyLong())).thenReturn(ModerateResult.allowed());
        when(chatRateLimiterService.isAllowed(anyLong())).thenReturn(true);
        when(liveStreamModerationService.isMuted(creatorId, userId)).thenReturn(false);
        when(liveStreamModerationService.isShadowMuted(creatorId, userId)).thenReturn(true);
        stubRoomAndCreator(roomId, creatorId, creatorId);

        chatMessageService.processMessage(userId, chatMessage, "127.0.0.1");

        // Verify public broadcast NEVER happens
        verify(messagingTemplate, never()).convertAndSend(startsWith("/exchange/amq.topic/chat."), any(ChatMessageDto.class));

        // Verify user RECEIVES their own message via private queue
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), eq("/queue/chat"), eq(chatMessage));
    }

    @Test
    void processMessage_WhenBannedFromRoom_ShouldNotBroadcastAndNotifyUser() {
        Long userId = 123L;
        Long creatorId = 456L;
        UUID roomId = UUID.randomUUID();
        ChatMessageDto chatMessage = new ChatMessageDto();
        chatMessage.setRoomId(roomId);
        chatMessage.setCreatorUserId(creatorId);
        chatMessage.setContent("Hello");

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setEmail("banned@test.com");
        user.setStatus(com.joinlivora.backend.user.UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(moderationService.isBanned(eq(userId), anyString())).thenReturn(true);
        stubRoomAndCreator(roomId, creatorId, creatorId);

        chatMessageService.processMessage(userId, chatMessage, "127.0.0.1");

        // Verify broadcast NEVER happens
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(ChatMessageDto.class));

        // Verify user IS notified of ban
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), eq("/queue/moderation"), argThat(msg ->
            ((RealtimeMessage)msg).getType().equals("MODERATION_BLOCKED") &&
            ((java.util.Map)((RealtimeMessage)msg).getPayload()).get("reason").toString().contains("banned")
        ));
    }

    @Test
    void processMessage_WhenSlowModeExceeded_ShouldNotBroadcastAndNotifyUser() {
        Long userId = 123L;
        Long creatorId = 456L;
        UUID roomId = UUID.randomUUID();
        ChatMessageDto chatMessage = new ChatMessageDto();
        chatMessage.setRoomId(roomId);
        chatMessage.setCreatorUserId(creatorId);
        chatMessage.setContent("Wait for it");

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setStatus(com.joinlivora.backend.user.UserStatus.ACTIVE);
        user.setRole(com.joinlivora.backend.user.Role.USER);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));

        when(moderationService.moderate(anyString(), anyLong(), anyLong())).thenReturn(ModerateResult.allowed());
        when(chatRateLimiterService.isAllowed(anyLong())).thenReturn(true);
        when(liveStreamModerationService.isMuted(creatorId, userId)).thenReturn(false);
        doThrow(new RuntimeException("Slow mode is active")).when(chatRateLimitService).validateMessageRate(userId, roomId);
        stubRoomAndCreator(roomId, creatorId, creatorId);

        chatMessageService.processMessage(userId, chatMessage, "127.0.0.1");

        // Verify broadcast NEVER happens
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(ChatMessageDto.class));

        // Verify user IS notified of rate limit
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), eq("/queue/moderation"), argThat(msg ->
            ((RealtimeMessage)msg).getType().equals("RATE_LIMIT_EXCEEDED")
        ));
    }

    @Test
    void processMessage_ShouldSanitizeContent() {
        Long userId = 123L;
        Long creatorId = 456L;
        UUID roomId = UUID.randomUUID();
        ChatMessageDto chatMessage = new ChatMessageDto();
        chatMessage.setRoomId(roomId);
        chatMessage.setCreatorUserId(creatorId);
        chatMessage.setContent("   Too many spaces and very long message... ".repeat(20));

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setStatus(com.joinlivora.backend.user.UserStatus.ACTIVE);
        user.setRole(com.joinlivora.backend.user.Role.USER);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(moderationService.moderate(anyString(), anyLong(), anyLong())).thenReturn(ModerateResult.allowed());
        when(chatRateLimiterService.isAllowed(anyLong())).thenReturn(true);
        stubRoomAndCreator(roomId, creatorId, creatorId);

        chatMessageService.processMessage(userId, chatMessage, "127.0.0.1");

        // Verify content was trimmed and truncated
        assertTrue(chatMessage.getContent().length() <= 500);
        assertFalse(chatMessage.getContent().startsWith(" "));
        assertFalse(chatMessage.getContent().endsWith(" "));

        // Verify broadcast uses sanitized content
        verify(chatBatchService).enqueueMessage(eq(creatorId), eq(chatMessage));
        assertTrue(chatMessage.getContent().length() <= 500);
    }

    @Test
    void processMessage_WhenV2Room_ShouldPersistMessage() {
        Long userId = 123L;
        Long creatorId = 456L;
        Long roomIdLong = 789L;
        UUID roomIdUuid = new UUID(0L, roomIdLong);
        ChatMessageDto chatMessage = new ChatMessageDto();
        chatMessage.setRoomId(roomIdUuid);
        chatMessage.setContent("Persistent V2 message");

        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(userId);
        user.setStatus(com.joinlivora.backend.user.UserStatus.ACTIVE);
        user.setRole(com.joinlivora.backend.user.Role.USER);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));

        com.joinlivora.backend.chat.domain.ChatRoom v2Room = new com.joinlivora.backend.chat.domain.ChatRoom();
        v2Room.setId(roomIdLong);
        v2Room.setCreatorId(creatorId);
        v2Room.setStatus(com.joinlivora.backend.chat.domain.ChatRoomStatus.ACTIVE);
        when(chatRoomService.getRoomEntity(roomIdUuid)).thenReturn(v2Room);

        com.joinlivora.backend.creator.model.Creator creator = mock(com.joinlivora.backend.creator.model.Creator.class);
        com.joinlivora.backend.user.User creatorUser = new com.joinlivora.backend.user.User();
        creatorUser.setId(creatorId);
        when(creator.getUser()).thenReturn(creatorUser);
        when(creatorRepository.findById(v2Room.getCreatorId())).thenReturn(java.util.Optional.of(creator));

        when(moderationService.moderate(anyString(), anyLong(), anyLong())).thenReturn(ModerateResult.allowed());
        when(chatRateLimiterService.isAllowed(anyLong())).thenReturn(true);

        chatMessageService.processMessage(userId, chatMessage, "127.0.0.1");

        // Verify entity was persisted asynchronously via ChatPersistenceService
        verify(chatPersistenceService).persistChatMessage(argThat(entity ->
            entity.getRoomId().equals(roomIdLong) &&
            entity.getContent().equals("Persistent V2 message")
        ));

        // Verify DTO was updated with a generated ID and timestamp
        assertNotNull(chatMessage.getId());
        assertNotNull(chatMessage.getTimestamp());
    }
}
