package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorStripeStatusResponse {
    private boolean hasAccount;
    private boolean onboardingCompleted;
    private boolean payoutsEnabled;
}
