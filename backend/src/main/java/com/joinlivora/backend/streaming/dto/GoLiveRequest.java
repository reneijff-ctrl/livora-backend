package com.joinlivora.backend.streaming.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoLiveRequest {
    private String title;
    private String description;
    private Long minChatTokens;

    @com.fasterxml.jackson.annotation.JsonProperty("isPaid")
    private boolean isPaid;

    private Long pricePerMessage;
    private BigDecimal admissionPrice;
    private boolean recordingEnabled;

    @com.fasterxml.jackson.annotation.JsonProperty("chatEnabled")
    @Builder.Default
    private boolean chatEnabled = true;

    @com.fasterxml.jackson.annotation.JsonProperty("slowMode")
    private boolean slowMode;

    private Integer slowModeInterval;
    private Integer maxViewers;
    private String streamCategory;
}
