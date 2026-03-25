package com.joinlivora.backend.token.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenTipRequest {
    private Long creatorId;
    private java.util.UUID roomId;
    private Integer amount;
    private String message;
    private String clientRequestId;
    private String giftName;
}
