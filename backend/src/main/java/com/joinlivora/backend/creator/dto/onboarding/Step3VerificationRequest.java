package com.joinlivora.backend.creator.dto.onboarding;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Step3VerificationRequest {
    private String governmentIdImage;
    private String selfieWithId;
}
