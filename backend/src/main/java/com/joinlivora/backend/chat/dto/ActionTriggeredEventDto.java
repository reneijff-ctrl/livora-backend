package com.joinlivora.backend.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionTriggeredEventDto {
    @Builder.Default
    private String type = "ACTION_TRIGGERED";
    private Long amount;
    private String description;
    private String donor;
}
