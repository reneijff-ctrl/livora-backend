package com.joinlivora.backend.presence.dto;

import com.joinlivora.backend.presence.model.CreatorAvailabilityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorAvailabilityResponse {
    private Long creatorUserId;
    private CreatorAvailabilityStatus availability;
    private boolean isPaid;
    private long viewerCount;
    private java.math.BigDecimal admissionPrice;
}
