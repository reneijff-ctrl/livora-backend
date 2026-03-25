package com.joinlivora.backend.streaming.chat.commands;

import com.joinlivora.backend.chat.dto.ChatMessageResponse;
import com.joinlivora.backend.streaming.ModerationSettings;
import com.joinlivora.backend.streaming.ModerationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RulesCommandHandler implements ChatCommandHandler {

    private final ModerationSettingsRepository moderationSettingsRepository;

    private static final String DEFAULT_RULES = "📜 Stream Rules:\n" +
            "1. Be respectful to others.\n" +
            "2. No hate speech or harassment.\n" +
            "3. No spamming.\n" +
            "4. Follow Livora terms of service.";

    @Override
    public boolean supports(String command) {
        return "rules".equalsIgnoreCase(command);
    }

    @Override
    public void execute(ChatCommandContext context) {
        Long creatorId = context.getCreatorId();

        String rules = buildRules(creatorId);

        ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .type("BOT")
                .senderId(0L)
                .senderUsername("Livora AI")
                .senderRole("BOT")
                .content(rules)
                .timestamp(Instant.now())
                .build();

        // Send rules only to the user who requested them to avoid spamming the chat
        context.sendToUser(response);
    }

    private String buildRules(Long creatorId) {
        try {
            Optional<ModerationSettings> settingsOpt = moderationSettingsRepository.findByCreatorUserId(creatorId);
            if (settingsOpt.isEmpty()) {
                return DEFAULT_RULES;
            }

            ModerationSettings settings = settingsOpt.get();
            StringBuilder sb = new StringBuilder(DEFAULT_RULES);

            if (settings.isStrictMode()) {
                sb.append("\n5. ⚠️ Strict mode is enabled — messages are actively moderated.");
            }

            String bannedWords = settings.getBannedWords();
            if (bannedWords != null && !bannedWords.isBlank()) {
                sb.append("\n\n🚫 Some words are restricted in this stream.");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to load moderation settings for creator {}: {}", creatorId, e.getMessage());
            return DEFAULT_RULES;
        }
    }
}
