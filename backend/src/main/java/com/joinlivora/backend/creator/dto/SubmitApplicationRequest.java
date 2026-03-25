package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitApplicationRequest {
    private boolean termsAccepted;
    private boolean ageVerified;
}
