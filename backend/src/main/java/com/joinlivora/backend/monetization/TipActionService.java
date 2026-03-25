package com.joinlivora.backend.monetization;

import com.joinlivora.backend.chat.dto.ActionTriggeredEventDto;
import com.joinlivora.backend.monetization.dto.TipActionDto;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import com.joinlivora.backend.websocket.LiveEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TipActionService {

    private final TipActionRepository tipActionRepository;
    private final TipMenuCategoryRepository categoryRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final StreamAssistantBotService streamAssistantBotService;

    @Transactional(readOnly = true)
    public List<TipActionDto> getAllActions(Long creatorId) {
        return tipActionRepository.findAllByCreatorId(creatorId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TipAction> getEnabledActions(Long creatorId) {
        log.info("MONETIZATION: Fetching enabled tip actions for creatorId: {}", creatorId);
        return tipActionRepository.findAllEnabledByCreatorId(creatorId);
    }

    @Transactional
    public TipActionDto createAction(Long creatorId, TipActionDto dto) {
        if (tipActionRepository.countByCreatorId(creatorId) >= 30) {
            throw new RuntimeException("Maximum of 30 tip actions reached.");
        }
        if (dto.getAmount() <= 0) {
            throw new RuntimeException("Tip amount must be greater than 0.");
        }

        // Validate category ownership if provided
        if (dto.getCategoryId() != null) {
            TipMenuCategory category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found."));
            if (!category.getCreatorId().equals(creatorId)) {
                throw new RuntimeException("Category does not belong to this creator.");
            }
        }

        TipAction action = TipAction.builder()
                .creatorId(creatorId)
                .amount(dto.getAmount())
                .description(dto.getDescription())
                .enabled(dto.isEnabled())
                .categoryId(dto.getCategoryId())
                .sortOrder(dto.getSortOrder())
                .build();

        return mapToDto(tipActionRepository.save(action));
    }

    @Transactional
    public TipActionDto updateAction(Long creatorId, UUID id, TipActionDto dto) {
        TipAction action = tipActionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tip action not found."));

        if (!action.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Not authorized to update this action.");
        }

        if (dto.getAmount() <= 0) {
            throw new RuntimeException("Tip amount must be greater than 0.");
        }

        // Validate category ownership if provided
        if (dto.getCategoryId() != null) {
            TipMenuCategory category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found."));
            if (!category.getCreatorId().equals(creatorId)) {
                throw new RuntimeException("Category does not belong to this creator.");
            }
        }

        action.setAmount(dto.getAmount());
        action.setDescription(dto.getDescription());
        action.setEnabled(dto.isEnabled());
        action.setCategoryId(dto.getCategoryId());
        action.setSortOrder(dto.getSortOrder());

        return mapToDto(tipActionRepository.save(action));
    }

    @Transactional
    public void deleteAction(Long creatorId, UUID id) {
        TipAction action = tipActionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tip action not found."));

        if (!action.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Not authorized to delete this action.");
        }

        tipActionRepository.delete(action);
    }

    @Transactional(readOnly = true)
    public void checkAction(Long creatorId, long amount, String donorName) {
        log.info("MONETIZATION: Checking tip action for creator {}, amount {}, donor {}", creatorId, amount, donorName);
        List<TipAction> matches = tipActionRepository.findAllByCreatorIdAndAmountAndEnabledTrue(creatorId, amount);
        
        if (!matches.isEmpty()) {
            TipAction action = matches.get(0); // Take first if multiple
            log.info("MONETIZATION: Tip action triggered: {}", action.getDescription());
            ActionTriggeredEventDto event = ActionTriggeredEventDto.builder()
                    .type("ACTION_TRIGGERED")
                    .amount(amount)
                    .description(action.getDescription())
                    .donor(donorName)
                    .build();

            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, event);
            // Broadcast to dedicated monetization stream
            messagingTemplate.convertAndSend("/exchange/amq.topic/monetization." + creatorId,
                    LiveEvent.of("ACTION_TRIGGERED", event));

            // Bot announcement for action trigger
            streamAssistantBotService.onActionTriggered(creatorId, donorName, amount, action.getDescription());
        } else {
            log.info("MONETIZATION: No matching enabled tip action found for creator {} and amount {}", creatorId, amount);
        }
    }

    private TipActionDto mapToDto(TipAction action) {
        return TipActionDto.builder()
                .id(action.getId())
                .creatorId(action.getCreatorId())
                .amount(action.getAmount())
                .description(action.getDescription())
                .enabled(action.isEnabled())
                .categoryId(action.getCategoryId())
                .sortOrder(action.getSortOrder())
                .build();
    }
}
