package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.chat.analysis.SentimentResult;
import com.joinlivora.backend.chat.dto.ModerateResult;
import com.joinlivora.backend.chat.dto.ModerationSeverity;
import com.joinlivora.backend.streaming.ModerationSettings;
import com.joinlivora.backend.streaming.ModerationSettingsRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.RealtimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatModerationServiceTest {

    @Mock
    private ModerationRepository moderationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private com.joinlivora.backend.chat.analysis.ToxicityAnalyzer toxicityAnalyzer;

    @Mock
    private com.joinlivora.backend.chat.analysis.SentimentAnalyzer sentimentAnalyzer;

    @Mock
    private ModerationSettingsRepository settingsRepository;

    @Mock
    private com.joinlivora.backend.streaming.StreamRepository streamRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private com.joinlivora.backend.admin.service.AdminRealtimeEventService adminRealtimeEventService;

    @Mock
    private com.joinlivora.backend.moderation.service.AIModerationEngineService aiModerationEngineService;

    @InjectMocks
    private ChatModerationService moderationService;

    private User testUser;
    private User moderator;
    private Long userId = 1L;
    private Long moderatorId = 2L;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("test@example.com");

        moderator = new User();
        moderator.setId(moderatorId);
        moderator.setEmail("mod@example.com");
        moderator.setRole(Role.ADMIN);

        // Setup default config values
        ReflectionTestUtils.setField(moderationService, "bannedWords", List.of("badword", "scam"));
        ReflectionTestUtils.setField(moderationService, "capsThreshold", 0.7);
        ReflectionTestUtils.setField(moderationService, "repeatedCharsThreshold", 6);
        ReflectionTestUtils.setField(moderationService, "duplicateWindowSeconds", 30);
        ReflectionTestUtils.setField(moderationService, "duplicateThreshold", 3);

        // Default sentiment: neutral/non-toxic
        lenient().when(sentimentAnalyzer.analyze(anyString())).thenReturn(
                SentimentResult.builder().score(0.0).positive(false).toxic(false).build()
        );
        // Default Redis ops
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void moderate_PositiveSentiment_ShouldHavePositiveFlag() {
        // Arrange
        String message = "I love this liveStream!";
        when(sentimentAnalyzer.analyze(message)).thenReturn(SentimentResult.builder()
                .positive(true)
                .score(0.8)
                .toxic(false)
                .build());

        // Act
        ModerateResult result = moderationService.moderate(message, userId, null);

        // Assert
        assertTrue(result.isAllowed());
        assertTrue(result.isPositive());
    }

    @Test
    void moderate_NormalMessage_ShouldBeAllowed() {
        // Sentiment defaults to neutral in setUp
        ModerateResult result = moderationService.moderate("Hello world!", userId, null);
        assertTrue(result.isAllowed());
    }

    @Test
    void moderate_ToxicContent_ShouldBeBlocked() {
        when(sentimentAnalyzer.analyze(anyString())).thenReturn(
                SentimentResult.builder().score(-0.5).positive(false).toxic(true).build()
        );
        ModerateResult result = moderationService.moderate("Some toxic message", userId, null);
        assertFalse(result.isAllowed());
        assertEquals("Message contains toxic content", result.getReason());
        assertEquals(ModerationSeverity.HIGH, result.getSeverity());
    }

    @Test
    void moderate_NegativeSentiment_ShouldBeBlocked() {
        when(sentimentAnalyzer.analyze(anyString())).thenReturn(
                SentimentResult.builder().score(-0.95).positive(false).toxic(false).build()
        );
        ModerateResult result = moderationService.moderate("I hate you so much", userId, null);
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("too negative"));
        assertEquals(ModerationSeverity.MEDIUM, result.getSeverity());
    }

    @Test
    void moderate_ExcessiveCaps_ShouldBeBlocked() {
        Long creatorId = 10L;
        UUID streamId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("shouter");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        com.joinlivora.backend.streaming.Stream stream = mock(com.joinlivora.backend.streaming.Stream.class);
        when(stream.getId()).thenReturn(streamId);
        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorId)).thenReturn(List.of(stream));

        ModerateResult result = moderationService.moderate("HELLO WORLD I AM SHOUTING", userId, creatorId);
        assertFalse(result.isAllowed());
        assertEquals("Excessive caps usage is not allowed", result.getReason());
        assertEquals(ModerationSeverity.LOW, result.getSeverity());

        verify(adminRealtimeEventService).broadcastChatSpamDetected(eq(streamId), eq("shouter"));
        verify(aiModerationEngineService).evaluateStreamRisk(eq(streamId), eq(0), eq(0), eq(25), eq(0));
    }

    @Test
    void moderate_RepeatedChars_ShouldBeBlocked() {
        Long creatorId = 10L;
        UUID streamId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("repeater");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        com.joinlivora.backend.streaming.Stream stream = mock(com.joinlivora.backend.streaming.Stream.class);
        when(stream.getId()).thenReturn(streamId);
        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorId)).thenReturn(List.of(stream));

        ModerateResult result = moderationService.moderate("aaaaaaa", userId, creatorId);
        assertFalse(result.isAllowed());
        assertEquals("Too many repeated characters", result.getReason());

        verify(adminRealtimeEventService).broadcastChatSpamDetected(eq(streamId), eq("repeater"));
        verify(aiModerationEngineService).evaluateStreamRisk(eq(streamId), eq(0), eq(0), eq(25), eq(0));
    }

    @Test
    void moderate_Links_ShouldBeBlocked() {
        ModerateResult result = moderationService.moderate("Check out https://scam.com", userId, null);
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("links"));
        assertEquals(ModerationSeverity.MEDIUM, result.getSeverity());
    }

    @Test
    void moderate_BannedWords_ShouldBeBlocked() {
        ModerateResult result = moderationService.moderate("This is a badword", userId, null);
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("prohibited"));
        assertEquals(ModerationSeverity.HIGH, result.getSeverity());
    }

    @Test
    void moderate_SystemMessage_ShouldBeAllowed() {
        // System message (userId is null)
        ModerateResult result = moderationService.moderate("https://scam.com AAAAAAA badword", null, null);
        assertTrue(result.isAllowed());
    }

    @Test
    void moderate_DuplicateSpam_ShouldBeBlockedOnThirdAttempt() {
        String message = "Hello spam";
        Long creatorId = 10L;
        UUID streamId = UUID.randomUUID();
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L, 2L, 3L);
        
        User user = new User();
        user.setId(userId);
        user.setUsername("spammer");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        com.joinlivora.backend.streaming.Stream stream = mock(com.joinlivora.backend.streaming.Stream.class);
        when(stream.getId()).thenReturn(streamId);
        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorId)).thenReturn(List.of(stream));

        // 1st
        assertTrue(moderationService.moderate(message, userId, creatorId).isAllowed());
        // 2nd
        assertTrue(moderationService.moderate(message, userId, creatorId).isAllowed());
        // 3rd
        ModerateResult result = moderationService.moderate(message, userId, creatorId);
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("repeatedly"));
        
        verify(adminRealtimeEventService).broadcastChatSpamDetected(eq(streamId), eq("spammer"));
        verify(aiModerationEngineService).evaluateStreamRisk(eq(streamId), eq(0), eq(0), eq(25), eq(0));
    }

    @Test
    void moderate_CreatorBannedWords_ShouldBeBlocked() {
        Long creatorId = 99L;
        String message = "This is a customban";
        ModerationSettings settings = ModerationSettings.builder()
                .creatorUserId(creatorId)
                .bannedWords("customban")
                .build();
        
        when(settingsRepository.findByCreatorUserId(creatorId)).thenReturn(Optional.of(settings));

        ModerateResult result = moderationService.moderate(message, userId, creatorId);

        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("prohibited by the creator"));
        assertEquals(ModerationSeverity.HIGH, result.getSeverity());
    }

    @Test
    void moderate_StrictMode_ShouldApplyLowerThresholds() {
        Long creatorId = 99L;
        // 5 characters, 3 are caps (60%) -> Global allows (0.7), but Strict (0.4) blocks
        String capsMessage = "ABCde"; 
        
        ModerationSettings settings = ModerationSettings.builder()
                .creatorUserId(creatorId)
                .strictMode(true)
                .build();
        
        when(settingsRepository.findByCreatorUserId(creatorId)).thenReturn(Optional.of(settings));

        // 1. Caps check
        ModerateResult capsResult = moderationService.moderate(capsMessage, userId, creatorId);
        assertFalse(capsResult.isAllowed());
        assertTrue(capsResult.getReason().contains("caps"));

        // 2. Repetition check (4 chars repeat)
        String repeatMessage = "aaaa";
        ModerateResult repeatResult = moderationService.moderate(repeatMessage, userId, creatorId);
        assertFalse(repeatResult.isAllowed());
        assertTrue(repeatResult.getReason().contains("repeated characters"));
    }

    @Test
    void deleteMessage_ShouldSaveModerationAndBroadcastDeleteEvent() {
        UUID liveStreamUuid = UUID.randomUUID();
        String roomId = "stream-" + liveStreamUuid;
        String messageId = "msg456";
        Long creatorId = 10L;

        com.joinlivora.backend.streaming.Stream room = mock(com.joinlivora.backend.streaming.Stream.class);
        User creator = new User();
        creator.setId(creatorId);
        when(room.getCreator()).thenReturn(creator);
        when(streamRepository.findById(liveStreamUuid)).thenReturn(Optional.of(room));

        when(userRepository.findById(moderatorId)).thenReturn(Optional.of(moderator));

        moderationService.deleteMessage(roomId, messageId, moderatorId);

        verify(moderationRepository).save(any(Moderation.class));
        ArgumentCaptor<RealtimeMessage> captor = ArgumentCaptor.forClass(RealtimeMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());

        RealtimeMessage message = captor.getValue();
        assertEquals("MESSAGE_DELETED", message.getType());
        assertEquals(messageId, message.getPayload().get("messageId"));
    }

    @Test
    void muteUser_ShouldSaveModerationAndBroadcast() {
        Duration duration = Duration.ofMinutes(10);
        UUID liveStreamUuid = UUID.randomUUID();
        String roomId = "stream-" + liveStreamUuid;
        Long creatorId = 10L;

        com.joinlivora.backend.streaming.Stream room = mock(com.joinlivora.backend.streaming.Stream.class);
        User creator = new User();
        creator.setId(creatorId);
        when(room.getCreator()).thenReturn(creator);
        when(streamRepository.findById(liveStreamUuid)).thenReturn(Optional.of(room));

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(moderatorId)).thenReturn(Optional.of(moderator));

        moderationService.muteUser(userId, moderatorId, duration, roomId);

        verify(moderationRepository).save(any(Moderation.class));
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(RealtimeMessage.class));
    }

    @Test
    void banUser_ShouldSaveModerationAndBroadcast() {
        UUID liveStreamUuid = UUID.randomUUID();
        String roomId = "stream-" + liveStreamUuid;
        Long creatorId = 10L;

        com.joinlivora.backend.streaming.Stream room = mock(com.joinlivora.backend.streaming.Stream.class);
        User creator = new User();
        creator.setId(creatorId);
        when(room.getCreator()).thenReturn(creator);
        when(streamRepository.findById(liveStreamUuid)).thenReturn(Optional.of(room));

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(moderatorId)).thenReturn(Optional.of(moderator));
        when(moderationRepository.existsByTargetUserAndActionAndRoomId(any(), any(), any())).thenReturn(false);

        moderationService.banUser(userId, moderatorId, roomId);

        verify(moderationRepository).save(any(Moderation.class));
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(RealtimeMessage.class));
        verify(messagingTemplate).convertAndSendToUser(eq(testUser.getId().toString()), eq("/queue/notifications"), any(RealtimeMessage.class));
    }

    @Test
    void isMuted_WhenMuted_ShouldReturnTrue() {
        String roomId = "room123";
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(moderationRepository.findTopByTargetUserAndActionAndExpiresAtAfterOrderByCreatedAtDesc(eq(testUser), eq(ModerationAction.MUTE), any(Instant.class)))
                .thenReturn(Optional.of(new Moderation()));

        assertTrue(moderationService.isMuted(userId, roomId));
    }

    @Test
    void isMuted_WhenNotMuted_ShouldReturnFalse() {
        String roomId = "room123";
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(moderationRepository.findTopByTargetUserAndActionAndExpiresAtAfterOrderByCreatedAtDesc(eq(testUser), eq(ModerationAction.MUTE), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertFalse(moderationService.isMuted(userId, roomId));
    }

    @Test
    void isBanned_WhenBanned_ShouldReturnTrue() {
        String roomId = "room123";
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(moderationRepository.existsByTargetUserAndActionAndRoomId(testUser, ModerationAction.BAN, roomId)).thenReturn(true);

        assertTrue(moderationService.isBanned(userId, roomId));
    }

    @Test
    void isBanned_Admin_ShouldReturnFalse() {
        testUser.setRole(Role.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertFalse(moderationService.isBanned(userId, "room123"));
        verify(moderationRepository, never()).existsByTargetUserAndActionAndRoomId(any(), any(), any());
    }

    @Test
    void isMuted_Admin_ShouldReturnFalse() {
        testUser.setRole(Role.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertFalse(moderationService.isMuted(userId, "room123"));
        verify(moderationRepository, never()).findTopByTargetUserAndActionAndExpiresAtAfterOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void isShadowMuted_WhenShadowbannedOnUser_ShouldReturnTrue() {
        testUser.setShadowbanned(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertTrue(moderationService.isShadowMuted(userId, "room1"));
    }

    @Test
    void isShadowMuted_WhenShadowMuteModerationActive_ShouldReturnTrue() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(moderationRepository.findTopByTargetUserAndActionAndExpiresAtAfterOrderByCreatedAtDesc(eq(testUser), eq(ModerationAction.SHADOW_MUTE), any(Instant.class)))
                .thenReturn(Optional.of(new Moderation()));

        assertTrue(moderationService.isShadowMuted(userId, "room1"));
    }

    @Test
    void shadowMuteUser_ShouldSaveModerationWithoutBroadcasting() {
        Duration duration = Duration.ofHours(1);
        String roomId = "room123";
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(moderatorId)).thenReturn(Optional.of(moderator));

        moderationService.shadowMuteUser(userId, moderatorId, duration, roomId);

        verify(moderationRepository).save(any(Moderation.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}









