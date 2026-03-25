package com.joinlivora.backend.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentHealthResponse {
    private String status;
    private boolean stripeEnabled;
    private boolean stripeConnected;
}
