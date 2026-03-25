package com.joinlivora.backend.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StripeCheckoutRequest {
    private Long creatorId;
    private BigDecimal amount;
}
