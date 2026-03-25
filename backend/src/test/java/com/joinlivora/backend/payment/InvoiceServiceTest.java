package com.joinlivora.backend.payment;

import com.joinlivora.backend.config.PlatformCompanyProperties;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceNumberGenerator invoiceNumberGenerator;
    @Mock
    private VatCalculationService vatCalculationService;
    @Mock
    private PlatformCompanyProperties companyProperties;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, invoiceNumberGenerator, vatCalculationService, companyProperties);
    }

    @Test
    void createInvoice_ValidData_ShouldSaveAndReturnInvoice() {
        User user = new User();
        user.setEmail("test@example.com");
        BigDecimal grossAmount = new BigDecimal("121.00");
        String currency = "EUR";
        String countryCode = "DE";
        InvoiceType type = InvoiceType.SUBSCRIPTION;
        String stripeInvoiceId = "in_123";

        VatCalculationService.VatResult vatResult = new VatCalculationService.VatResult(
                new BigDecimal("100.00"),
                new BigDecimal("21.00"),
                new BigDecimal("121.00"),
                new BigDecimal("0.21")
        );

        when(vatCalculationService.calculateVatFromGross(grossAmount, countryCode, false)).thenReturn(vatResult);
        when(invoiceNumberGenerator.generateNextInvoiceNumber()).thenReturn("INV-2026-000001");
        when(companyProperties.getName()).thenReturn("Livora Platform");
        when(companyProperties.getAddress()).thenReturn("Platform Address");
        when(companyProperties.getVatNumber()).thenReturn("VAT123");
        when(companyProperties.getEmail()).thenReturn("billing@joinlivora.com");
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice result = invoiceService.createInvoice(user, grossAmount, currency, countryCode, type, stripeInvoiceId, "Test Name", "123 Street, City, DE");

        assertNotNull(result);
        assertEquals("INV-2026-000001", result.getInvoiceNumber());
        assertEquals(new BigDecimal("121.00"), result.getGrossAmount());
        assertEquals(new BigDecimal("21.00"), result.getVatAmount());
        assertEquals(new BigDecimal("100.00"), result.getNetAmount());
        assertEquals("eur", result.getCurrency());
        assertEquals("DE", result.getCountryCode());
        assertEquals(type, result.getInvoiceType());
        assertEquals(stripeInvoiceId, result.getStripeInvoiceId());
        assertEquals("Test Name", result.getBillingName());
        assertEquals("123 Street, City, DE", result.getBillingAddress());
        assertEquals("test@example.com", result.getBillingEmail());
        assertEquals("Livora Platform", result.getSellerName());
        assertEquals("Platform Address", result.getSellerAddress());
        assertEquals("billing@joinlivora.com", result.getSellerEmail());
        assertEquals("VAT123", result.getSellerVatNumber());
        
        verify(invoiceRepository).save(any(Invoice.class));
    }
}








