package com.joinlivora.backend.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDTO {
    private String id;
    private String name;
    private String price;
    private String currency;
    private String interval;
    private List<String> features;
    private boolean isPopular;
    private String stripePriceId;
}
