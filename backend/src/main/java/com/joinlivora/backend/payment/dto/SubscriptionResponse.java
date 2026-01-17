package com.joinlivora.backend.payment.dto;

import com.joinlivora.backend.payment.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {
    private SubscriptionStatus status;
    private Instant currentPeriodEnd;
    private boolean cancelAtPeriodEnd;
    private Instant nextInvoiceDate;
    private String paymentMethodBrand;
    private String last4;
}
