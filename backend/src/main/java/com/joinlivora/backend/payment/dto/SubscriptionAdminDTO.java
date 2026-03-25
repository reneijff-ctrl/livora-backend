package com.joinlivora.backend.payment.dto;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionAdminDTO(
    UUID id,
    String userEmail,
    String status,
    Instant createdAt
) {}
