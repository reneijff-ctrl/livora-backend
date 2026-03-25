package com.joinlivora.backend.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StripeVerificationResponse {
    private String status;
    private Long amount;

    public static StripeVerificationResponse paid(Long amount) {
        return new StripeVerificationResponse("COMPLETED", amount);
    }

    public static StripeVerificationResponse failed() {
        return new StripeVerificationResponse("FAILED", null);
    }
}
