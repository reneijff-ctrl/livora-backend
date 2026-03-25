package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.TaxSummaryDTO;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class TaxServiceTest {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UserRepository userRepository;

    private TaxService taxService;

    @BeforeEach
    void setUp() {
        taxService = new TaxService(invoiceRepository);
    }

    @Test
    void testGetTaxSummary() {
        User user = TestUserFactory.createViewer("creator@example.com");
        user = userRepository.save(user);

        Instant now = Instant.now();

        // Invoice 1: FR
        invoiceRepository.save(Invoice.builder()
                .userId(user)
                .invoiceNumber("INV-001")
                .grossAmount(new BigDecimal("120.00"))
                .vatAmount(new BigDecimal("20.00"))
                .netAmount(new BigDecimal("100.00"))
                .currency("eur")
                .countryCode("FR")
                .invoiceType(InvoiceType.SUBSCRIPTION)
                .status(InvoiceStatus.PAID)
                .issuedAt(now.minus(5, ChronoUnit.DAYS))
                .build());

        // Invoice 2: FR
        invoiceRepository.save(Invoice.builder()
                .userId(user)
                .invoiceNumber("INV-002")
                .grossAmount(new BigDecimal("60.00"))
                .vatAmount(new BigDecimal("10.00"))
                .netAmount(new BigDecimal("50.00"))
                .currency("eur")
                .countryCode("FR")
                .invoiceType(InvoiceType.PPV)
                .status(InvoiceStatus.PAID)
                .issuedAt(now.minus(2, ChronoUnit.DAYS))
                .build());

        // Invoice 3: DE
        invoiceRepository.save(Invoice.builder()
                .userId(user)
                .invoiceNumber("INV-003")
                .grossAmount(new BigDecimal("119.00"))
                .vatAmount(new BigDecimal("19.00"))
                .netAmount(new BigDecimal("100.00"))
                .currency("eur")
                .countryCode("DE")
                .invoiceType(InvoiceType.TOKENS)
                .status(InvoiceStatus.PAID)
                .issuedAt(now.minus(1, ChronoUnit.DAYS))
                .build());

        // Invoice 4: Outside period
        invoiceRepository.save(Invoice.builder()
                .userId(user)
                .invoiceNumber("INV-004")
                .grossAmount(new BigDecimal("100.00"))
                .vatAmount(new BigDecimal("20.00"))
                .netAmount(new BigDecimal("80.00"))
                .currency("eur")
                .countryCode("FR")
                .invoiceType(InvoiceType.SUBSCRIPTION)
                .status(InvoiceStatus.PAID)
                .issuedAt(now.minus(10, ChronoUnit.DAYS))
                .build());

        TaxSummaryDTO summary = taxService.getTaxSummary(now.minus(7, ChronoUnit.DAYS), now.plus(1, ChronoUnit.MINUTES));

        assertEquals(0, new BigDecimal("250.00").compareTo(summary.getTotalRevenue()));
        assertEquals(0, new BigDecimal("49.00").compareTo(summary.getTotalVat()));
        assertEquals(2, summary.getRevenueByCountry().size());
        assertEquals(0, new BigDecimal("150.00").compareTo(summary.getRevenueByCountry().get("FR")));
        assertEquals(0, new BigDecimal("100.00").compareTo(summary.getRevenueByCountry().get("DE")));
    }
}








