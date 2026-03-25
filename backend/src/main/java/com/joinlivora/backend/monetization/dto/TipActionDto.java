package com.joinlivora.backend.monetization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipActionDto {
    private UUID id;
    private Long creatorId;
    private Long amount;
    private String description;
    @JsonAlias("isEnabled")
    private boolean enabled;
    private UUID categoryId;
    private int sortOrder;
}
