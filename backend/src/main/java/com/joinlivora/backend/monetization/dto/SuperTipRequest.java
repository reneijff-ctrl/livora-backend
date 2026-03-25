package com.joinlivora.backend.monetization.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperTipRequest {
    @Min(value = 1, message = "Amount must be at least 1")
    private long amount;
    
    private String message;

    @NotBlank(message = "clientRequestId is required")
    private String clientRequestId;
}
