package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreatorStripeStatusResponse {
    private Long userId;
    private String email;
    private String stripeAccountId;
    private boolean payoutsEnabled;
    private boolean stripeOnboardingComplete;
}
