package com.joinlivora.backend.streaming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LivestreamAccessResponse {
    private boolean hasAccess;
    private boolean isPaid;
    private boolean isLive;
    private long viewerCount;
    private java.math.BigDecimal admissionPrice;
    private java.util.UUID streamId;
    private java.util.UUID streamRoomId;
}
