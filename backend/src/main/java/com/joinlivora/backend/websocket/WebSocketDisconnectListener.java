package com.joinlivora.backend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Previously handled SessionDisconnectEvent for Mediasoup transport cleanup
 * and viewer count decrement. This logic has been consolidated into
 * {@link WebSocketSessionCleanupService} to ensure cleanup executes exactly once
 * per disconnect event.
 *
 * This class is intentionally kept empty to preserve the bean registration
 * and avoid breaking any component-scan expectations.
 */
@Slf4j
@Component
public class WebSocketDisconnectListener {
    // All disconnect cleanup logic has been moved to WebSocketSessionCleanupService
}
