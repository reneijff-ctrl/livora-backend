package com.joinlivora.backend.chat.dto;

import com.joinlivora.backend.chat.ChatMode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatModeRequest {
    @NotNull(message = "chatMode is required")
    private ChatMode chatMode;
}
