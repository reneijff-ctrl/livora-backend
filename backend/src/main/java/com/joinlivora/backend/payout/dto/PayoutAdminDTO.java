package com.joinlivora.backend.payout.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutAdminDTO(
    UUID id,
    String userEmail,
    BigDecimal amount,
    String status,
    Instant createdAt
) {}
