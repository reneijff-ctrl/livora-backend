package com.joinlivora.backend.payment.dto;

import com.joinlivora.backend.payment.SubscriptionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AdminSubscriptionResponse {
    private UUID id;
    private String userEmail;
    private SubscriptionStatus status;
    private String stripeSubscriptionId;
    private Instant createdAt;
}
