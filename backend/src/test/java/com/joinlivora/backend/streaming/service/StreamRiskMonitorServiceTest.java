package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.abuse.model.AbuseEventType;
import com.joinlivora.backend.abuse.repository.AbuseEventRepository;
import com.joinlivora.backend.admin.dto.StreamRiskStatusDTO;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.websocket.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamRiskMonitorServiceTest {

    @Mock
    private StreamRepository streamRepository;
    @Mock
    private LiveViewerCounterService viewerCounterService;
    @Mock
    private RuleFraudSignalRepository ruleFraudSignalRepository;
    @Mock
    private AbuseEventRepository abuseEventRepository;
    @Mock
    private PresenceService presenceService;

    @InjectMocks
    private StreamRiskMonitorService streamRiskMonitorService;

    private UUID streamId;
    private Long creatorId;
    private User creator;
    private Stream stream;

    @BeforeEach
    void setUp() {
        streamId = UUID.randomUUID();
        creatorId = 123L;
        creator = new User();
        creator.setId(creatorId);
        creator.setUsername("testcreator");

        stream = new Stream();
        stream.setId(streamId);
        stream.setCreator(creator);
    }

    @Test
    void getStreamRisk_LowRisk() {
        when(streamRepository.findById(streamId)).thenReturn(Optional.of(stream));
        when(viewerCounterService.getViewerCount(creatorId)).thenReturn(50L);
        when(viewerCounterService.getPreviousViewerCount(streamId)).thenReturn(45L);
        when(ruleFraudSignalRepository.countByCreatorIdAndTypeAndCreatedAtAfter(anyLong(), any(), any())).thenReturn(0L);
        when(abuseEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);
        when(presenceService.getRecentNewAccountJoinCount(anyLong(), any())).thenReturn(0L);

        StreamRiskStatusDTO risk = streamRiskMonitorService.getStreamRisk(streamId);

        assertEquals(RiskLevel.LOW, risk.getRiskLevel());
        assertEquals(0, risk.getRiskScore());
        assertFalse(risk.isViewerSpike());
        assertFalse(risk.isSuspiciousTips());
        assertFalse(risk.isChatSpam());
        assertFalse(risk.isNewAccountCluster());
    }

    @Test
    void getStreamRisk_CriticalRisk_AllSignals() {
        when(streamRepository.findById(streamId)).thenReturn(Optional.of(stream));
        
        // Spike: 10 viewers -> 150 viewers (+140, >100)
        when(viewerCounterService.getViewerCount(creatorId)).thenReturn(150L);
        when(viewerCounterService.getPreviousViewerCount(streamId)).thenReturn(10L);
        
        // Suspicious tips
        when(ruleFraudSignalRepository.countByCreatorIdAndTypeAndCreatedAtAfter(eq(creatorId), eq(FraudSignalType.RAPID_TIP_REPEATS), any()))
                .thenReturn(1L);
        
        // Chat spam
        when(abuseEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(eq(new UUID(0L, creatorId)), eq(AbuseEventType.MESSAGE_SPAM), any()))
                .thenReturn(1L);
        
        // New account cluster
        when(presenceService.getRecentNewAccountJoinCount(eq(creatorId), any(Duration.class))).thenReturn(6L);

        StreamRiskStatusDTO risk = streamRiskMonitorService.getStreamRisk(streamId);

        // 30 (spike) + 40 (tips) + 20 (spam) + 35 (cluster) = 125
        assertEquals(125, risk.getRiskScore());
        assertEquals(RiskLevel.CRITICAL, risk.getRiskLevel());
        assertTrue(risk.isViewerSpike());
        assertTrue(risk.isSuspiciousTips());
        assertTrue(risk.isChatSpam());
        assertTrue(risk.isNewAccountCluster());
    }

    @Test
    void getStreamRisk_HighRisk_SomeSignals() {
        when(streamRepository.findById(streamId)).thenReturn(Optional.of(stream));
        when(viewerCounterService.getViewerCount(creatorId)).thenReturn(50L);
        when(viewerCounterService.getPreviousViewerCount(streamId)).thenReturn(45L);
        
        // Suspicious tips (40)
        when(ruleFraudSignalRepository.countByCreatorIdAndTypeAndCreatedAtAfter(eq(creatorId), eq(FraudSignalType.RAPID_TIP_REPEATS), any()))
                .thenReturn(1L);
        
        // New account cluster (35)
        when(presenceService.getRecentNewAccountJoinCount(eq(creatorId), any(Duration.class))).thenReturn(5L);
        
        when(abuseEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);

        StreamRiskStatusDTO risk = streamRiskMonitorService.getStreamRisk(streamId);

        // 40 + 35 = 75
        assertEquals(75, risk.getRiskScore());
        assertEquals(RiskLevel.HIGH, risk.getRiskLevel());
        assertFalse(risk.isViewerSpike());
        assertTrue(risk.isSuspiciousTips());
        assertFalse(risk.isChatSpam());
        assertTrue(risk.isNewAccountCluster());
    }
}
