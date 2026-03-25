package com.joinlivora.backend.payment;

import com.joinlivora.backend.config.PlatformCompanyProperties;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service("invoiceService")
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final VatCalculationService vatCalculationService;
    private final PlatformCompanyProperties companyProperties;

    @Transactional
    public Invoice createInvoice(User user, BigDecimal grossAmount, String currency, String countryCode, InvoiceType type, String stripeInvoiceId, String billingName, String billingAddress) {
        if (stripeInvoiceId != null) {
            java.util.Optional<Invoice> existing = invoiceRepository.findByStripeInvoiceId(stripeInvoiceId);
            if (existing.isPresent()) {
                log.info("Invoice already exists for Stripe Invoice ID: {}", stripeInvoiceId);
                return existing.get();
            }
        }

        log.info("Generating invoice for creator {} type {} amount {} {}", user.getEmail(), type, grossAmount, currency);

        // Calculate VAT based on gross amount and country code
        // For now, we assume users are not VAT exempt (B2C)
        VatCalculationService.VatResult vatResult = vatCalculationService.calculateVatFromGross(grossAmount, countryCode, false);

        Invoice invoice = Invoice.builder()
                .userId(user)
                .invoiceNumber(invoiceNumberGenerator.generateNextInvoiceNumber())
                .grossAmount(vatResult.grossAmount())
                .vatAmount(vatResult.vatAmount())
                .netAmount(vatResult.netAmount())
                .currency(currency.toLowerCase())
                .countryCode(countryCode != null ? countryCode.toUpperCase() : "XX")
                .invoiceType(type)
                .stripeInvoiceId(stripeInvoiceId)
                .billingName(billingName != null ? billingName : user.getEmail())
                .billingAddress(billingAddress)
                .billingEmail(user.getEmail())
                .sellerName(companyProperties.getName())
                .sellerAddress(companyProperties.getAddress())
                .sellerEmail(companyProperties.getEmail())
                .sellerVatNumber(companyProperties.getVatNumber())
                .status(InvoiceStatus.PAID)
                .issuedAt(Instant.now())
                .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);
        log.info("Invoice created: {} for Stripe Invoice: {}", savedInvoice.getInvoiceNumber(), stripeInvoiceId);
        
        return savedInvoice;
    }
}
