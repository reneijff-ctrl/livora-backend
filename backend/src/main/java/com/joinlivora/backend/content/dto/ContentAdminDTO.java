package com.joinlivora.backend.content.dto;

import java.time.Instant;
import java.util.UUID;

public record ContentAdminDTO(
    UUID id,
    String creatorEmail,
    String title,
    String status,
    Instant createdAt
) {}
