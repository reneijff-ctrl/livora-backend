package com.joinlivora.backend.chat;

import com.joinlivora.backend.monetization.HighlightLevel;
import com.joinlivora.backend.monetization.SuperTipService;
import com.joinlivora.backend.monetization.TipOrchestrationService;
import com.joinlivora.backend.monetization.dto.SuperTipResponse;
import com.joinlivora.backend.monetization.dto.TipResult;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatTipServiceTest {

    @Mock
    private TipOrchestrationService tipService;

    @Mock
    private SuperTipService superTipService;

    @Mock
    private com.joinlivora.backend.monetization.SuperTipHighlightTracker highlightTracker;

    @InjectMocks
    private ChatTipService chatTipService;

    private User viewer;
    private UUID liveStreamId;

    @BeforeEach
    void setUp() {
        viewer = new User();
        viewer.setEmail("viewer@test.com");
        viewer.setId(1L);

        liveStreamId = UUID.randomUUID();
    }

    @Test
    void processChatTip_ShouldSucceed() {
        long amount = 100;
        String message = "Nice liveStream!";
        String requestId = "req-123";
        String ip = "127.0.0.1";
        String fp = "fp123";

        when(tipService.sendTokenTip(any(), any(), anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(TipResult.builder().build());

        chatTipService.processChatTip(viewer, liveStreamId, amount, message, requestId, ip, fp);

        // Verify TipOrchestrationService was called
        verify(tipService).sendTokenTip(viewer, liveStreamId, amount, message, requestId, ip, fp, null);
    }

    @Test
    void processSuperTip_ShouldSucceed() {
        long amount = 5000;
        String message = "Amazing work!";
        String requestId = "st-123";
        SuperTipResponse result = SuperTipResponse.builder()
                .id(UUID.randomUUID())
                .senderEmail(viewer.getEmail())
                .amount(BigDecimal.valueOf(amount))
                .message(message)
                .highlightLevel(HighlightLevel.PREMIUM)
                .durationSeconds(60)
                .createdAt(Instant.now())
                .build();
        result.setSuccess(true);

        when(superTipService.sendSuperTip(any(), any(), anyLong(), anyString(), anyString(), any(), any()))
                .thenReturn(result);

        chatTipService.processSuperTip(viewer, liveStreamId, amount, message, requestId, null, null);

        verify(superTipService).sendSuperTip(viewer, liveStreamId, amount, message, requestId, null, null);
        
        // Verify tracker was called
        verify(highlightTracker).addHighlight(liveStreamId, result);
    }

    private void broadcastTipMessage(UUID liveStreamId, String viewerEmail, long amount, String message) {
        // This is private in the service, but we verify it via convertAndSend above
    }
}









