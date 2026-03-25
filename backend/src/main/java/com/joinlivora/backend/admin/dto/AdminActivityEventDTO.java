package com.joinlivora.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminActivityEventDTO {
    private String id;
    private String type;
    private String description;
    private Instant timestamp;
}
