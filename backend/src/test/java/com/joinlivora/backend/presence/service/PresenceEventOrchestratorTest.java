package com.joinlivora.backend.presence.service;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PresenceEventOrchestratorTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;

    @Mock
    private StreamAssistantBotService streamAssistantBotService;

    @Mock
    private ChatRoomService chatRoomServiceV2;

    @Mock
    private BrokerAvailabilityListener brokerAvailabilityListener;

    private PresenceEventOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new PresenceEventOrchestrator(
                messagingTemplate,
                analyticsEventPublisher,
                streamAssistantBotService,
                chatRoomServiceV2,
                brokerAvailabilityListener,
                null
        );
    }

    @Test
    void whenBrokerAvailable_shouldSendMessage() {
        when(brokerAvailabilityListener.isBrokerAvailable()).thenReturn(true);

        orchestrator.broadcastPresence(10L);

        verify(messagingTemplate, times(1)).convertAndSend(eq("/exchange/amq.topic/presence"), any(Map.class));
    }

    @Test
    void whenBrokerNotAvailable_shouldSkipSendMessage() {
        when(brokerAvailabilityListener.isBrokerAvailable()).thenReturn(false);

        orchestrator.broadcastPresence(10L);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Map.class));
    }

    @Test
    void whenBrokerNotAvailable_shouldStillPerformNonMessagingOperations() {
        lenient().when(brokerAvailabilityListener.isBrokerAvailable()).thenReturn(false);

        orchestrator.notifyStreamJoin(1L, "viewer");

        verify(streamAssistantBotService, times(1)).onUserJoined(1L, "viewer");
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
