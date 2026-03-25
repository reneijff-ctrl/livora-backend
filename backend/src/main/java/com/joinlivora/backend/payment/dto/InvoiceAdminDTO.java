package com.joinlivora.backend.payment.dto;

import com.joinlivora.backend.payment.Invoice;
import com.joinlivora.backend.payment.InvoiceStatus;
import com.joinlivora.backend.payment.InvoiceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceAdminDTO {
    private UUID id;
    private String invoiceNumber;
    private Long userId;
    private String userEmail;
    private BigDecimal grossAmount;
    private BigDecimal vatAmount;
    private BigDecimal netAmount;
    private String currency;
    private String countryCode;
    private InvoiceType invoiceType;
    private InvoiceStatus status;
    private Instant issuedAt;
    private String billingName;

    public static InvoiceAdminDTO fromEntity(Invoice invoice) {
        return InvoiceAdminDTO.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .userId(invoice.getUserId().getId())
                .userEmail(invoice.getUserId().getEmail())
                .grossAmount(invoice.getGrossAmount())
                .vatAmount(invoice.getVatAmount())
                .netAmount(invoice.getNetAmount())
                .currency(invoice.getCurrency())
                .countryCode(invoice.getCountryCode())
                .invoiceType(invoice.getInvoiceType())
                .status(invoice.getStatus())
                .issuedAt(invoice.getIssuedAt())
                .billingName(invoice.getBillingName())
                .build();
    }
}
