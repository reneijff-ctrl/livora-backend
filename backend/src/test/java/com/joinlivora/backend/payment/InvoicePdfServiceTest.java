package com.joinlivora.backend.payment;

import com.joinlivora.backend.config.PlatformCompanyProperties;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InvoicePdfServiceTest {

    private InvoicePdfService invoicePdfService;
    private PlatformCompanyProperties companyProperties;

    @BeforeEach
    void setUp() {
        companyProperties = new PlatformCompanyProperties();
        invoicePdfService = new InvoicePdfService(companyProperties);
    }

    @Test
    void generateInvoicePdf_ShouldReturnByteArray() throws IOException {
        User user = new User();
        user.setId(1L);
        user.setEmail("creator@example.com");

        Invoice invoice = Invoice.builder()
                .userId(user)
                .invoiceNumber("INV-2026-000001")
                .grossAmount(new BigDecimal("121.00"))
                .vatAmount(new BigDecimal("21.00"))
                .netAmount(new BigDecimal("100.00"))
                .currency("EUR")
                .countryCode("BE")
                .invoiceType(InvoiceType.SUBSCRIPTION)
                .billingName("John Doe")
                .billingAddress("Main Street 1, Brussels")
                .billingEmail("creator@example.com")
                .sellerName("Livora")
                .sellerAddress("Platform HQ")
                .sellerEmail("billing@joinlivora.com")
                .sellerVatNumber("VAT999")
                .status(InvoiceStatus.PAID)
                .issuedAt(Instant.now())
                .build();

        byte[] pdfBytes = invoicePdfService.generateInvoicePdf(invoice);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateCreatorEarningsInvoicePdf_ShouldReturnByteArray() throws IOException {
        User creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@example.com");

        com.joinlivora.backend.payout.CreatorEarningsInvoice invoice = com.joinlivora.backend.payout.CreatorEarningsInvoice.builder()
                .creator(creator)
                .invoiceNumber("CR-INV-202512-1-EUR")
                .periodStart(Instant.now().minusSeconds(86400 * 30))
                .periodEnd(Instant.now())
                .grossEarnings(new BigDecimal("1000.00"))
                .platformFee(new BigDecimal("300.00"))
                .netEarnings(new BigDecimal("700.00"))
                .currency("EUR")
                .creatorName("Creator Name")
                .creatorEmail("creator@example.com")
                .sellerName("Livora")
                .sellerAddress("Platform HQ")
                .sellerEmail("billing@joinlivora.com")
                .sellerVatNumber("VAT999")
                .status(com.joinlivora.backend.payout.CreatorEarningsInvoiceStatus.GENERATED)
                .createdAt(Instant.now())
                .build();

        byte[] pdfBytes = invoicePdfService.generateCreatorEarningsInvoicePdf(invoice);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }
}








