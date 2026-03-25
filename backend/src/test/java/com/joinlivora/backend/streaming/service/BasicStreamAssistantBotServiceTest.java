package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.monetization.TipActionService;
import com.joinlivora.backend.monetization.TipGoalService;
import com.joinlivora.backend.monetization.TipMenuCategory;
import com.joinlivora.backend.monetization.TipMenuCategoryService;
import com.joinlivora.backend.privateshow.CreatorPrivateSettings;
import com.joinlivora.backend.privateshow.CreatorPrivateSettingsService;
import com.joinlivora.backend.privateshow.PrivateSessionRepository;
import com.joinlivora.backend.privateshow.PrivateSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BasicStreamAssistantBotServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private CreatorRepository creatorRepository;

    @Mock
    private TipGoalService tipGoalService;

    @Mock
    private TipActionService tipActionService;

    @Mock
    private TipMenuCategoryService tipMenuCategoryService;

    @Mock
    private CreatorPrivateSettingsService creatorPrivateSettingsService;

    @Mock
    private PrivateSessionRepository privateSessionRepository;

    private BasicStreamAssistantBotService botService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        botService = new BasicStreamAssistantBotService(messagingTemplate, creatorRepository, tipGoalService, tipActionService, tipMenuCategoryService, creatorPrivateSettingsService, privateSessionRepository);

        // Default: tip menu categories are enabled
        when(tipMenuCategoryService.getEnabledCategories(anyLong())).thenReturn(List.of(new TipMenuCategory()));

        // Default mock for creator username lookup
        when(creatorRepository.findUsernameByUserId(anyLong())).thenReturn(Optional.of("SuperCreator"));
    }

    @Test
    void onTipReceived_ShouldBroadcastThankYouMessage() {
        Long creatorId = 123L;
        botService.onTipReceived(creatorId, "Alice", 50.0, "USD");

        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());

        ChatMessageDto result = captor.getValue();
        assertEquals("BOT", result.getType());
        assertEquals("SuperCreator", result.getSenderUsername());
        String message = result.getContent();
        assertTrue(message.contains("Alice"));
        assertTrue(message.contains("$50.00"));
        assertTrue(message.contains("Thank you"));
    }

    @Test
    void onTipReceived_ShouldFormatTokenTipsCorrectly() {
        Long creatorId = 123L;
        botService.onTipReceived(creatorId, "Alice", 1000.0, "TOKEN");

        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());

        ChatMessageDto result = captor.getValue();
        assertEquals("BOT", result.getType());
        assertEquals("SuperCreator", result.getSenderUsername());
        String message = result.getContent();
        assertTrue(message.contains("Alice"));
        assertTrue(message.contains("1000 tokens"));
        assertTrue(message.contains("tipping"));
    }

    @Test
    void onPositiveMessage_ShouldBroadcastEncouragement() {
        Long creatorId = 123L;
        botService.onPositiveMessage(creatorId, "Bob", "This is great!");

        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());

        ChatMessageDto result = captor.getValue();
        assertEquals("BOT", result.getType());
        assertEquals("SuperCreator", result.getSenderUsername());
        String message = result.getContent();
        assertTrue(message.contains("Bob"));
        assertTrue(message.contains("energy"));
    }

    @Test
    void onUserJoined_ShouldBroadcastWelcomeMessage() {
        Long creatorId = 123L;
        botService.onUserJoined(creatorId, "Charlie");

        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());

        ChatMessageDto result = captor.getValue();
        assertEquals("BOT", result.getType());
        assertEquals("SuperCreator", result.getSenderUsername());
        String message = result.getContent();
        assertTrue(message.contains("Charlie"));
        assertTrue(message.contains("Welcome"));
    }

    @Test
    void onUserJoined_ShouldNotBroadcastForAnonymous() {
        Long creatorId = 123L;
        botService.onUserJoined(creatorId, "anonymous");

        verify(messagingTemplate, never()).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(ChatMessageDto.class));
    }

    @Test
    void getCurrencySymbol_ShouldReturnCorrectSymbols() {
        Long creatorId = 123L;

        // Use different creatorIds to avoid cooldown interference between calls
        botService.onTipReceived(1L, "Alice", 10.0, "EUR");
        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.1"), captor.capture());
        assertTrue(captor.getValue().getContent().contains("€10.00"));

        botService.onTipReceived(2L, "Alice", 10.0, "GBP");
        captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.2"), captor.capture());
        assertTrue(captor.getValue().getContent().contains("£10.00"));

        // Unknown currency should not default to $
        botService.onTipReceived(3L, "Alice", 10.0, "XYZ");
        captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat.3"), captor.capture());
        assertTrue(captor.getValue().getContent().contains("XYZ10.00"));
    }
    @Test
    void onMessageReceived_ShouldTriggerReminderAfterFiveMessages() {
        Long creatorId = 123L;

        // Send 4 messages - no reminder
        for (int i = 0; i < 4; i++) {
            botService.onMessageReceived(creatorId);
        }
        verify(messagingTemplate, never()).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(ChatMessageDto.class));

        // 5th message - reminder triggered
        botService.onMessageReceived(creatorId);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(ChatMessageDto.class));
    }

    @Test
    void onMessageReceived_ShouldRespectCooldown() {
        Long creatorId = 123L;

        // Trigger first reminder
        for (int i = 0; i < 5; i++) {
            botService.onMessageReceived(creatorId);
        }
        verify(messagingTemplate, times(1)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(ChatMessageDto.class));

        // Send another 5 messages immediately - should NOT trigger due to 60s cooldown
        for (int i = 0; i < 5; i++) {
            botService.onMessageReceived(creatorId);
        }
        verify(messagingTemplate, times(1)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(ChatMessageDto.class));
    }

    @Test
    void onTipReceived_ShouldTriggerTipMenuReminderAfterTwoTips() {
        Long creatorId = 123L;
        String user = "TipperUser";

        // 1st tip - only thank you message
        botService.onTipReceived(creatorId, user, 10.0, "TOKEN");
        verify(messagingTemplate, times(1)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(ChatMessageDto.class));

        // 2nd tip - tipmenu reminder (thank you suppressed by 30s cooldown)
        botService.onTipReceived(creatorId, user, 10.0, "TOKEN");
        // Total 2 messages: 1 thank you + 1 reminder
        verify(messagingTemplate, times(2)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(ChatMessageDto.class));

        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate, times(2)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());

        boolean foundReminder = captor.getAllValues().stream()
                .anyMatch(msg -> msg.getContent().contains("/tipmenu"));
        assertTrue(foundReminder);
    }

    @Test
    void onTipReceived_ShouldNotTriggerReminderIfAlreadyUsedTipMenu() {
        Long creatorId = 123L;
        String user = "SmartUser";

        botService.onTipMenuUsed(creatorId, user);

        // 1st and 2nd tip - should only trigger thank you messages (2nd suppressed by cooldown)
        botService.onTipReceived(creatorId, user, 10.0, "TOKEN");
        botService.onTipReceived(creatorId, user, 10.0, "TOKEN");

        // Only 1 thank you (2nd suppressed by 30s cooldown), no reminder
        verify(messagingTemplate, times(1)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(ChatMessageDto.class));

        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());

        boolean foundReminder = captor.getAllValues().stream()
                .anyMatch(msg -> msg.getContent().contains("/tipmenu"));
        assertTrue(!foundReminder);
    }

    @Test
    void onTipReceived_ContextualReminderShouldRespectCooldown() {
        Long creatorId = 123L;
        String user = "ActiveTipper";

        // Trigger 1st reminder (needs 2 tips)
        botService.onTipReceived(creatorId, user, 10.0, "TOKEN");
        botService.onTipReceived(creatorId, user, 10.0, "TOKEN");

        // 1 thank you + 1 reminder = 2 (2nd thank you suppressed by 30s cooldown)
        verify(messagingTemplate, times(2)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(ChatMessageDto.class));

        // 3rd tip immediately - should NOT trigger another reminder (cooldown)
        botService.onTipReceived(creatorId, user, 10.0, "TOKEN");

        // Total still 2 (thank you and reminder both suppressed by cooldowns)
        verify(messagingTemplate, times(2)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(ChatMessageDto.class));
    }

    @Test
    void onNewFollow_ShouldBroadcastFollowMessage() {
        Long creatorId = 123L;
        botService.onNewFollow(creatorId, "NewFan");

        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());

        ChatMessageDto result = captor.getValue();
        assertEquals("BOT", result.getType());
        assertEquals("SuperCreator", result.getSenderUsername());
        assertTrue(result.getContent().contains("NewFan"));
        assertTrue(result.getContent().contains("Thank you for following"));
    }

    @Test
    void onNewFollow_ShouldNotBroadcastForBlankUsername() {
        Long creatorId = 123L;
        botService.onNewFollow(creatorId, "");
        botService.onNewFollow(creatorId, null);

        verify(messagingTemplate, never()).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(ChatMessageDto.class));
    }

    @Test
    void onMessageReceived_ShouldSendPrivateShowPromoWhenEnabled() {
        Long creatorId = 456L;
        CreatorPrivateSettings settings = new CreatorPrivateSettings();
        settings.setCreatorId(creatorId);
        settings.setEnabled(true);
        settings.setPricePerMinute(100L);
        when(creatorPrivateSettingsService.getOrCreate(creatorId)).thenReturn(settings);
        when(privateSessionRepository.existsByCreator_IdAndStatus(creatorId, PrivateSessionStatus.ACTIVE)).thenReturn(false);

        // Trigger enough messages to pass the tip menu reminder logic
        for (int i = 0; i < 5; i++) {
            botService.onMessageReceived(creatorId);
        }

        // Should have sent at least one private show promo
        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());

        boolean foundPromo = captor.getAllValues().stream()
                .anyMatch(msg -> msg.getContent().contains("tokens/min"));
        assertTrue(foundPromo);
    }

    @Test
    void onMessageReceived_ShouldNotSendPromoWhenPrivateShowDisabled() {
        Long creatorId = 789L;
        CreatorPrivateSettings settings = new CreatorPrivateSettings();
        settings.setCreatorId(creatorId);
        settings.setEnabled(false);
        settings.setPricePerMinute(100L);
        when(creatorPrivateSettingsService.getOrCreate(creatorId)).thenReturn(settings);

        botService.onMessageReceived(creatorId);

        // No promo should be sent (tip menu may send, but no "tokens/min")
        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate, atMost(1)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());

        boolean foundPromo = captor.getAllValues().stream()
                .anyMatch(msg -> msg.getContent().contains("tokens/min"));
        assertTrue(!foundPromo);
    }

    @Test
    void onMessageReceived_ShouldNotSendPromoWhenActivePrivateSession() {
        Long creatorId = 321L;
        CreatorPrivateSettings settings = new CreatorPrivateSettings();
        settings.setCreatorId(creatorId);
        settings.setEnabled(true);
        settings.setPricePerMinute(50L);
        when(creatorPrivateSettingsService.getOrCreate(creatorId)).thenReturn(settings);
        when(privateSessionRepository.existsByCreator_IdAndStatus(creatorId, PrivateSessionStatus.ACTIVE)).thenReturn(true);

        botService.onMessageReceived(creatorId);

        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate, atMost(1)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());

        boolean foundPromo = captor.getAllValues().stream()
                .anyMatch(msg -> msg.getContent().contains("tokens/min"));
        assertTrue(!foundPromo);
    }
}








