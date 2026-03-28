package com.joinlivora.backend.streaming.chat.commands;

import com.joinlivora.backend.chat.dto.ChatMessageResponse;
import com.joinlivora.backend.creator.model.Creator;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.privateshow.CreatorPrivateSettings;
import com.joinlivora.backend.privateshow.CreatorPrivateSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrivateShowCommandHandler implements ChatCommandHandler {

    private final CreatorPrivateSettingsService privateSettingsService;
    private final CreatorRepository creatorRepository;

    @Override
    public boolean supports(String command) {
        return "private".equalsIgnoreCase(command);
    }

    @Override
    public void execute(ChatCommandContext context) {
        Long creatorUserId = context.getCreatorId();

        String content;
        try {
            // Resolve Creator PK from user ID
            Optional<Creator> creatorOpt = creatorRepository.findByUser_Id(creatorUserId);
            if (creatorOpt.isEmpty()) {
                content = "❌ Creator not found.";
            } else {
                Long creatorId = creatorOpt.get().getId();
                CreatorPrivateSettings settings = privateSettingsService.getOrCreate(creatorId);

                if (!settings.isEnabled()) {
                    content = "🔒 Private shows are not available for this creator.";
                } else {
                    content = String.format(
                            "🔒 Private Shows Available!\nPrice: %d tokens/min\nTo request a private show, use the private show button on the stream page.",
                            settings.getPricePerMinute()
                    );
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch private show settings for creator {}: {}", creatorUserId, e.getMessage());
            content = "🔒 Private show info is currently unavailable.";
        }

        ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .type("BOT")
                .senderId(0L)
                .senderUsername("Livora AI")
                .senderRole("BOT")
                .content(content)
                .timestamp(Instant.now())
                .build();

        context.sendToUser(response);
    }
}
