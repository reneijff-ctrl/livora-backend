package com.joinlivora.backend.exception;

import com.joinlivora.backend.chat.dto.ChatErrorCode;
import lombok.Getter;
import org.springframework.security.access.AccessDeniedException;

@Getter
public class ChatAccessException extends AccessDeniedException {
    private final ChatErrorCode errorCode;
    private final java.util.UUID roomId;
    private final java.util.UUID ppvContentId;
    private final java.math.BigDecimal requiredPrice;

    public ChatAccessException(ChatErrorCode errorCode, String message) {
        this(errorCode, message, null, null, null);
    }

    public ChatAccessException(ChatErrorCode errorCode, String message, java.util.UUID roomId, java.util.UUID ppvContentId, java.math.BigDecimal requiredPrice) {
        super(message);
        this.errorCode = errorCode;
        this.roomId = roomId;
        this.ppvContentId = ppvContentId;
        this.requiredPrice = requiredPrice;
    }
}
