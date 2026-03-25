package com.joinlivora.backend.chat;

import com.joinlivora.backend.monetization.SuperTipService;
import com.joinlivora.backend.monetization.TipOrchestrationService;
import com.joinlivora.backend.monetization.dto.SuperTipResponse;
import com.joinlivora.backend.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class ChatTipService {

    private final TipOrchestrationService tipService;
    private final SuperTipService superTipService;
    private final com.joinlivora.backend.monetization.SuperTipHighlightTracker highlightTracker;

    public ChatTipService(
            TipOrchestrationService tipService,
            SuperTipService superTipService,
            com.joinlivora.backend.monetization.SuperTipHighlightTracker highlightTracker) {
        this.tipService = tipService;
        this.superTipService = superTipService;
        this.highlightTracker = highlightTracker;
    }

    /**
     * Processes a token tip sent in chat.
     * Delegates to TipOrchestrationService for core business logic and emits a chat system message.
     *
     * @param viewer   The creator sending the tip
     * @param streamId The ID of the stream room
     * @param amount   The amount of tokens to tip
     * @param message  An optional message to accompany the tip
     */
    @Transactional
    public void processChatTip(User viewer, UUID streamId, long amount, String message, String clientRequestId, String ipAddress, String fingerprintHash) {
        log.info("CHAT: Processing token tip of {} from {} in stream {} (Req: {})", amount, viewer.getEmail(), streamId, clientRequestId);

        // 1. Process via TipOrchestrationService
        tipService.sendTokenTip(viewer, streamId, amount, message, clientRequestId, ipAddress, fingerprintHash, null);

        if (log.isDebugEnabled()) {
            log.debug("CHAT: Token tip request processed for {}", clientRequestId);
        }
    }

    /**
     * Processes a SuperTip sent in chat.
     */
    @Transactional
    public void processSuperTip(User viewer, UUID streamId, long amount, String message, String clientRequestId, String ipAddress, String fingerprintHash) {
        log.info("CHAT: Processing SuperTip of {} from {} in stream {} (Req: {})", amount, viewer.getEmail(), streamId, clientRequestId);

        // 1. Process via SuperTipService
        SuperTipResponse result = superTipService.sendSuperTip(viewer, streamId, amount, message, clientRequestId, ipAddress, fingerprintHash);

        // 3. Track highlight (this handles queuing and broadcasting of the SUPER_TIP event)
        highlightTracker.addHighlight(streamId, result);
    }

}
