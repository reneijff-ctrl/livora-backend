package com.joinlivora.backend.streaming.chat.commands;

import com.joinlivora.backend.chat.dto.ChatMessageResponse;
import com.joinlivora.backend.chat.dto.TipMenuEventDto;
import com.joinlivora.backend.monetization.TipAction;
import com.joinlivora.backend.monetization.TipActionService;
import com.joinlivora.backend.monetization.TipMenuCategory;
import com.joinlivora.backend.monetization.TipMenuCategoryService;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class TipMenuCommandHandler implements ChatCommandHandler {

    private final TipActionService tipActionService;
    private final TipMenuCategoryService categoryService;
    private final StreamAssistantBotService streamAssistantBotService;

    @Override
    public boolean supports(String command) {
        return "tipmenu".equalsIgnoreCase(command);
    }

    @Override
    public void execute(ChatCommandContext context) {
        Long creatorId = context.getCreatorId();
        log.info("TIP_MENU: Processing command for creatorId: {}", creatorId);

        // Notify bot service that tipmenu was used (for contextual reminders)
        streamAssistantBotService.onTipMenuUsed(creatorId, context.getSenderUsername());
        
        List<TipAction> actions = tipActionService.getEnabledActions(creatorId);
        log.info("TIP_MENU: Found {} enabled actions for creatorId: {}", actions.size(), creatorId);

        if (actions.isEmpty()) {
            ChatMessageResponse response = ChatMessageResponse.builder()
                    .messageId(UUID.randomUUID().toString())
                    .type("SYSTEM")
                    .senderId(0L)
                    .senderUsername("System")
                    .senderRole("SYSTEM")
                    .content("No tip menu configured.")
                    .timestamp(Instant.now())
                    .build();
            context.sendToRoom(response);
        } else {
            // Build flat actions list (backward compatible)
            List<TipMenuEventDto.TipActionDto> actionDtos = actions.stream()
                    .map(a -> TipMenuEventDto.TipActionDto.builder()
                            .amount(a.getAmount())
                            .description(a.getDescription())
                            .build())
                    .collect(Collectors.toList());

            // Build grouped structure by category
            List<TipMenuCategory> enabledCategories = categoryService.getEnabledCategories(creatorId);
            Map<UUID, List<TipAction>> actionsByCategory = actions.stream()
                    .filter(a -> a.getCategoryId() != null)
                    .collect(Collectors.groupingBy(TipAction::getCategoryId));

            List<TipMenuEventDto.TipCategoryDto> categoryDtos = enabledCategories.stream()
                    .filter(cat -> actionsByCategory.containsKey(cat.getId()))
                    .map(cat -> TipMenuEventDto.TipCategoryDto.builder()
                            .title(cat.getTitle())
                            .actions(actionsByCategory.get(cat.getId()).stream()
                                    .map(a -> TipMenuEventDto.TipActionDto.builder()
                                            .amount(a.getAmount())
                                            .description(a.getDescription())
                                            .build())
                                    .collect(Collectors.toList()))
                            .build())
                    .collect(Collectors.toList());

            List<TipMenuEventDto.TipActionDto> uncategorizedDtos = actions.stream()
                    .filter(a -> a.getCategoryId() == null)
                    .map(a -> TipMenuEventDto.TipActionDto.builder()
                            .amount(a.getAmount())
                            .description(a.getDescription())
                            .build())
                    .collect(Collectors.toList());

            TipMenuEventDto event = TipMenuEventDto.builder()
                    .actions(actionDtos)
                    .categories(categoryDtos)
                    .uncategorized(uncategorizedDtos)
                    .build();

            context.sendToRoom(event);
            // Broadcast to dedicated monetization stream
            context.sendToMonetization("TIP_MENU", event);
        }
    }
}
