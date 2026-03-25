package com.joinlivora.backend.creator.dto;

import com.joinlivora.backend.creator.verification.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorVerificationStatusUpdate {
    @NotNull(message = "Status is required")
    private VerificationStatus status;
    private String rejectionReason;
}
