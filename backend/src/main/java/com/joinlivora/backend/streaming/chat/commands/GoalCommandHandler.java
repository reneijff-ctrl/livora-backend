package com.joinlivora.backend.streaming.chat.commands;

import com.joinlivora.backend.chat.dto.ChatMessageResponse;
import com.joinlivora.backend.chat.dto.GoalStatusEventDto;
import com.joinlivora.backend.monetization.TipGoal;
import com.joinlivora.backend.monetization.TipGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GoalCommandHandler implements ChatCommandHandler {

    private final TipGoalService tipGoalService;

    @Override
    public boolean supports(String command) {
        return "goal".equalsIgnoreCase(command);
    }

    @Override
    public void execute(ChatCommandContext context) {
        Long creatorId = context.getCreatorId();
        Optional<TipGoal> goalOpt = tipGoalService.getActiveGoal(creatorId);

        if (goalOpt.isEmpty()) {
            ChatMessageResponse response = ChatMessageResponse.builder()
                    .messageId(UUID.randomUUID().toString())
                    .type("SYSTEM")
                    .senderId(0L)
                    .senderUsername("System")
                    .senderRole("SYSTEM")
                    .content("No active goal.")
                    .timestamp(Instant.now())
                    .build();
            context.sendToRoom(response);
        } else {
            TipGoal goal = goalOpt.get();

            int percentage = 0;
            if (goal.getTargetAmount() > 0) {
                percentage = (int) ((goal.getCurrentAmount() * 100) / goal.getTargetAmount());
                if (percentage > 100) percentage = 100;
            }

            GoalStatusEventDto event = GoalStatusEventDto.builder()
                    .title(goal.getTitle())
                    .targetAmount(goal.getTargetAmount())
                    .currentAmount(goal.getCurrentAmount())
                    .percentage(percentage)
                    .active(goal.isActive())
                    .build();

            context.sendToRoom(event);
        }
    }
}
