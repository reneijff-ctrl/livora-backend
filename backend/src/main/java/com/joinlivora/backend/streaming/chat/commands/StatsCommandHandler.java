package com.joinlivora.backend.streaming.chat.commands;

import com.joinlivora.backend.chat.dto.ChatMessageResponse;
import com.joinlivora.backend.streaming.dto.LivestreamAnalyticsResponse;
import com.joinlivora.backend.streaming.service.LivestreamAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StatsCommandHandler implements ChatCommandHandler {

    private final LivestreamAnalyticsService livestreamAnalyticsService;

    @Override
    public boolean supports(String command) {
        return "stats".equalsIgnoreCase(command);
    }

    @Override
    public void execute(ChatCommandContext context) {
        Long creatorId = context.getCreatorId();

        String stats;
        try {
            LivestreamAnalyticsResponse analytics = livestreamAnalyticsService.getCurrentStats(creatorId);
            if (analytics == null) {
                stats = "📊 Stream stats are currently unavailable.";
            } else {
                long durationSeconds = analytics.getStreamDurationSeconds();
                long hours = durationSeconds / 3600;
                long minutes = (durationSeconds % 3600) / 60;
                String uptime = hours > 0
                        ? String.format("%dh %dm", hours, minutes)
                        : String.format("%dm", minutes);

                stats = String.format("📊 Stream Stats:\nViewers: %d\nPeak Viewers: %d\nUptime: %s",
                        analytics.getCurrentViewers(),
                        analytics.getPeakViewers(),
                        uptime);
            }
        } catch (Exception e) {
            log.error("Failed to fetch stream stats for creator {}: {}", creatorId, e.getMessage());
            stats = "📊 Stream stats are currently unavailable.";
        }

        ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .type("BOT")
                .senderId(0L)
                .senderUsername("Livora AI")
                .senderRole("BOT")
                .content(stats)
                .timestamp(Instant.now())
                .build();

        context.sendToUser(response);
    }
}
