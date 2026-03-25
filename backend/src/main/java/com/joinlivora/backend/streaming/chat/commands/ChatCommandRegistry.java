package com.joinlivora.backend.streaming.chat.commands;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ChatCommandRegistry {

    private final List<ChatCommandHandler> handlers;
    private final Map<String, Instant> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_SECONDS = 5;
    private static final long COOLDOWN_EXPIRY_SECONDS = 300;

    public ChatCommandRegistry(List<ChatCommandHandler> handlers) {
        this.handlers = handlers;
    }

    public void handleCommand(ChatCommandContext context) {
        String message = context.getFullMessage();
        if (message == null || !message.startsWith("/")) {
            return;
        }

        String username = context.getSenderUsername();
        if (isRateLimited(username)) {
            log.warn("Command: User {} is rate limited", username);
            return;
        }

        String command = extractCommand(message);
        handlers.stream()
                .filter(h -> h.supports(command))
                .findFirst()
                .ifPresentOrElse(
                        h -> {
                            try {
                                h.execute(context);
                                updateCooldown(username);
                            } catch (Exception e) {
                                log.error("Command: Error executing command {}: {}", command, e.getMessage());
                            }
                        },
                        () -> log.debug("Command: No handler found for {}", command)
                );
    }

    private String extractCommand(String message) {
        String firstWord = message.split("\\s+")[0];
        return firstWord.substring(1).toLowerCase();
    }

    private boolean isRateLimited(String username) {
        Instant lastExecution = cooldowns.get(username);
        if (lastExecution == null) {
            return false;
        }
        return Instant.now().isBefore(lastExecution.plusSeconds(COOLDOWN_SECONDS));
    }

    private void updateCooldown(String username) {
        cooldowns.put(username, Instant.now());
    }

    /**
     * Periodically evicts stale cooldown entries to prevent unbounded memory growth.
     */
    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void evictStaleCooldowns() {
        Instant cutoff = Instant.now().minusSeconds(COOLDOWN_EXPIRY_SECONDS);
        int before = cooldowns.size();
        cooldowns.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        int removed = before - cooldowns.size();
        if (removed > 0) {
            log.debug("Command: Evicted {} stale cooldown entries", removed);
        }
    }
}
