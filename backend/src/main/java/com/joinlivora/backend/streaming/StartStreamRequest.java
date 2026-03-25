package com.joinlivora.backend.streaming;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class StartStreamRequest {
    private String title;
    private String description;
    private Long minChatTokens;

    @com.fasterxml.jackson.annotation.JsonProperty("isPaid")
    private boolean isPaid;

    private Long pricePerMessage;
    private BigDecimal admissionPrice;
    private boolean recordingEnabled;
}
