package com.joinlivora.backend.chat;

import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.chat.dto.ChatMessageRequest;
import com.joinlivora.backend.chat.dto.ModerateResult;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.domain.ChatRoomStatus;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.chat.repository.ChatMessageRepository;
import com.joinlivora.backend.fraud.service.VelocityTrackerService;
import com.joinlivora.backend.abuse.AbuseDetectionService;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import com.joinlivora.backend.streaming.service.StreamModerationService;
import com.joinlivora.backend.streaming.service.StreamModeratorService;
import com.joinlivora.backend.moderation.CreatorRoomBanService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ListOperations;
import com.joinlivora.backend.chat.RedisChatBatchService;
import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.abuse.RestrictionService;
import com.joinlivora.backend.chat.SlowModeBypassService;
import com.joinlivora.backend.chat.ChatRateLimitService;
import com.joinlivora.backend.monetization.HighlightedMessageService;
import com.joinlivora.backend.streaming.StreamService;
import com.joinlivora.backend.streaming.chat.commands.ChatCommandRegistry;
import com.joinlivora.backend.websocket.PresenceService;
import com.joinlivora.backend.chat.service.ChatRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceV2BugTest {

    @Mock private VelocityTrackerService velocityTrackerService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private UserRepository userRepository;
    @Mock private AbuseDetectionService abuseDetectionService;
    @Mock private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock private ChatModerationService moderationService;
    @Mock private ChatRateLimiterService chatRateLimiterService;
    @Mock private CreatorRepository creatorRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatViolationLogRepository violationLogRepository;
    @Mock private StreamModerationService streamModerationService;
    @Mock private StreamModeratorService streamModeratorService;
    @Mock private CreatorRoomBanService creatorRoomBanService;
    @Mock private StreamAssistantBotService streamAssistantBotService;
    @Mock private TokenService tokenService;
    @Mock private CreatorEarningsService creatorEarningsService;
    @Mock private RestrictionService restrictionService;
    @Mock private SlowModeBypassService slowModeBypassService;
    @Mock private ChatRateLimitService chatRateLimitService;
    @Mock private HighlightedMessageService highlightedMessageService;
    @Mock private StreamService streamService;
    @Mock private StreamRepository streamRepository;
    @Mock private ChatCommandRegistry commandRegistry;
    @Mock private PresenceService presenceService;
    @Mock private ChatRoomService chatRoomService;
    @Mock private ChatPersistenceService chatPersistenceService;
    @Mock private RedisChatBatchService chatBatchService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ListOperations<String, Object> listOperations;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Test
    void processIncomingMessage_WhenRoomIsPaused_ShouldThrowException() {
        // Arrange
        Long creatorUserId = 1001L; // User ID of creator
        Long creatorEntityId = 5001L; // Creator entity ID
        Long actualRoomId = 9001L; // Real room ID

        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("Hello");
        request.setCreatorUserId(creatorUserId);
        request.setType("CHAT");
        User sender = new User();
        sender.setId(2001L);
        sender.setEmail("viewer@test.com");
        sender.setStatus(UserStatus.ACTIVE);
        sender.setRole(Role.USER);
        sender.setUsername("viewer");

        User creatorUser = new User();
        creatorUser.setId(creatorUserId);
        creatorUser.setEmail("creator@test.com");
        creatorUser.setStatus(UserStatus.ACTIVE);
        creatorUser.setRole(Role.CREATOR);

        when(userRepository.findById(2001L)).thenReturn(Optional.of(sender));
        lenient().when(moderationService.moderate(anyString(), anyLong(), anyLong())).thenReturn(ModerateResult.allowed());
        lenient().when(chatRateLimiterService.isAllowed(anyLong())).thenReturn(true);
        lenient().when(restrictionService.getActiveRestriction(any())).thenReturn(Optional.empty());

        // The fix: ChatMessageService calls chatRoomService.getOrCreateRoom(creatorUserId)
        ChatRoom actualRoom = new ChatRoom();
        actualRoom.setId(actualRoomId);
        actualRoom.setCreatorId(creatorEntityId);
        actualRoom.setStatus(ChatRoomStatus.PAUSED);
        
        // processIncomingMessage resolves room by creatorUserId
        when(chatRoomService.getOrCreateRoom(creatorUserId)).thenReturn(actualRoom);

        com.joinlivora.backend.creator.model.Creator creator = mock(com.joinlivora.backend.creator.model.Creator.class);
        lenient().when(creator.getUser()).thenReturn(creatorUser);
        lenient().when(creatorRepository.findById(creatorEntityId)).thenReturn(Optional.of(creator));

        // Act & Assert
        // This should throw because it found the actual room and it's PAUSED
        Exception exception = assertThrows(com.joinlivora.backend.exception.BusinessException.class, () -> {
            chatMessageService.processIncomingMessage(request, sender);
        });
        
        assertTrue(exception.getMessage().contains("CHAT_ROOM_NOT_ACTIVE"));
    }

    @Test
    void processIncomingMessage_WhenRoomIsActive_ShouldPersistInCorrectRoom() {
        // Arrange
        Long creatorUserId = 1001L;
        Long creatorEntityId = 5001L;
        Long actualRoomId = 9001L;

        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("Hello");
        request.setCreatorUserId(creatorUserId);
        request.setType("CHAT");
        User sender = new User();
        sender.setId(2001L);
        sender.setEmail("viewer@test.com");
        sender.setStatus(UserStatus.ACTIVE);
        sender.setRole(Role.USER);
        sender.setUsername("viewer");

        when(userRepository.findById(2001L)).thenReturn(Optional.of(sender));
        lenient().when(moderationService.moderate(anyString(), anyLong(), anyLong())).thenReturn(ModerateResult.allowed());
        lenient().when(chatRateLimiterService.isAllowed(anyLong())).thenReturn(true);
        lenient().when(restrictionService.getActiveRestriction(any())).thenReturn(Optional.empty());

        ChatRoom actualRoom = new ChatRoom();
        actualRoom.setId(actualRoomId);
        actualRoom.setCreatorId(creatorEntityId);
        actualRoom.setStatus(ChatRoomStatus.ACTIVE);
        
        // processIncomingMessage resolves room by creatorUserId
        when(chatRoomService.getOrCreateRoom(creatorUserId)).thenReturn(actualRoom);

        com.joinlivora.backend.creator.model.Creator creator = mock(com.joinlivora.backend.creator.model.Creator.class);
        User creatorUser = new User();
        creatorUser.setId(creatorUserId);
        lenient().when(creator.getUser()).thenReturn(creatorUser);
        lenient().when(creatorRepository.findById(creatorEntityId)).thenReturn(Optional.of(creator));

        // Act
        chatMessageService.processIncomingMessage(request, sender);

        // Assert
        // Message SHOULD be persisted in the room with ID 9001 (NOT the User ID 1001)
        verify(chatPersistenceService).persistChatMessage(argThat(msg -> msg.getRoomId().equals(actualRoomId)));

        // It should broadcast via batch service
        verify(chatBatchService).enqueueMessage(eq(creatorUserId), any(ChatMessageDto.class));
    }
}
