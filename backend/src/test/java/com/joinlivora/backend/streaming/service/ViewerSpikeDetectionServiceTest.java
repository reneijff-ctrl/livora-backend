package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.moderation.service.AIModerationEngineService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewerSpikeDetectionServiceTest {

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private LiveViewerCounterService viewerCounterService;

    @Mock
    private AdminRealtimeEventService adminRealtimeEventService;

    @Mock
    private AIModerationEngineService aiModerationEngineService;

    private ViewerSpikeDetectionService viewerSpikeDetectionService;

    @BeforeEach
    void setUp() {
        viewerSpikeDetectionService = new ViewerSpikeDetectionService(
            streamRepository, 
            viewerCounterService, 
            adminRealtimeEventService,
            aiModerationEngineService
        );
    }

    @Test
    void detectViewerSpikes_ShouldTriggerSpike_WhenDeltaThresholdExceeded() {
        // Given
        UUID streamId = UUID.randomUUID();
        Long creatorId = 1L;
        
        Stream stream = mock(Stream.class);
        User creator = mock(User.class);
        String creatorUsername = "test_creator";
        
        when(stream.getId()).thenReturn(streamId);
        when(stream.getCreator()).thenReturn(creator);
        when(creator.getId()).thenReturn(creatorId);
        when(creator.getUsername()).thenReturn(creatorUsername);
        when(streamRepository.findActiveStreamsWithUser()).thenReturn(List.of(stream));
        
        // First poll: 50 viewers
        when(viewerCounterService.getViewerCount(creatorId)).thenReturn(50L);
        viewerSpikeDetectionService.detectViewerSpikes();
        
        // Second poll: 160 viewers (delta = 110)
        when(viewerCounterService.getViewerCount(creatorId)).thenReturn(160L);
        
        // When
        viewerSpikeDetectionService.detectViewerSpikes();
        
        // Then
        verify(adminRealtimeEventService).publishViewerSpike(eq(streamId), eq(110));
        verify(adminRealtimeEventService).publishAbuseEvent(
            eq("VIEWER_SPIKE"),
            eq(streamId),
            eq(creatorUsername),
            eq("Viewer spike detected")
        );
        verify(aiModerationEngineService).evaluateStreamRisk(eq(streamId), eq(30), anyInt(), anyInt(), anyInt());
    }

    @Test
    void detectViewerSpikes_ShouldTriggerSpike_WhenGrowthThresholdExceeded() {
        // Given
        UUID streamId = UUID.randomUUID();
        Long creatorId = 1L;
        
        Stream stream = mock(Stream.class);
        User creator = mock(User.class);
        String creatorUsername = "test_creator";
        
        when(stream.getId()).thenReturn(streamId);
        when(stream.getCreator()).thenReturn(creator);
        when(creator.getId()).thenReturn(creatorId);
        when(creator.getUsername()).thenReturn(creatorUsername);
        when(streamRepository.findActiveStreamsWithUser()).thenReturn(List.of(stream));
        
        // First poll: 20 viewers
        when(viewerCounterService.getViewerCount(creatorId)).thenReturn(20L);
        viewerSpikeDetectionService.detectViewerSpikes();
        
        // Second poll: 90 viewers (delta = 70, growth = 3.5x > 3.0x)
        when(viewerCounterService.getViewerCount(creatorId)).thenReturn(90L);
        
        // When
        viewerSpikeDetectionService.detectViewerSpikes();
        
        // Then
        verify(adminRealtimeEventService).publishViewerSpike(eq(streamId), eq(70));
        verify(adminRealtimeEventService).publishAbuseEvent(
            eq("VIEWER_SPIKE"),
            eq(streamId),
            eq(creatorUsername),
            eq("Viewer spike detected")
        );
        verify(aiModerationEngineService).evaluateStreamRisk(eq(streamId), eq(30), anyInt(), anyInt(), anyInt());
    }

    @Test
    void detectViewerSpikes_ShouldNotTriggerSpike_WhenBelowThresholds() {
        // Given
        UUID streamId = UUID.randomUUID();
        Long creatorId = 1L;
        
        Stream stream = mock(Stream.class);
        User creator = mock(User.class);
        
        when(stream.getId()).thenReturn(streamId);
        when(stream.getCreator()).thenReturn(creator);
        when(creator.getId()).thenReturn(creatorId);
        when(streamRepository.findActiveStreamsWithUser()).thenReturn(List.of(stream));
        
        // First poll: 100 viewers
        when(viewerCounterService.getViewerCount(creatorId)).thenReturn(100L);
        viewerSpikeDetectionService.detectViewerSpikes();
        
        // Second poll: 150 viewers (delta = 50 < 100, growth = 0.5x < 3.0x)
        when(viewerCounterService.getViewerCount(creatorId)).thenReturn(150L);
        
        // When
        viewerSpikeDetectionService.detectViewerSpikes();
        
        // Then
        verify(adminRealtimeEventService, never()).publishViewerSpike(any(), anyInt());
    }
}
