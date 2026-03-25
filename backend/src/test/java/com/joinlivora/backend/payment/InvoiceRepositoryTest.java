package com.joinlivora.backend.payment;

import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class InvoiceRepositoryTest {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testSaveAndFindInvoice() {
        User user = TestUserFactory.createViewer("test@example.com");
        user = userRepository.save(user);

        Invoice invoice = Invoice.builder()
                .userId(user)
                .invoiceNumber("INV-001")
                .grossAmount(new BigDecimal("100.00"))
                .vatAmount(new BigDecimal("20.00"))
                .netAmount(new BigDecimal("80.00"))
                .currency("EUR")
                .countryCode("FR")
                .invoiceType(InvoiceType.SUBSCRIPTION)
                .status(InvoiceStatus.PAID)
                .issuedAt(Instant.now())
                .build();

        Invoice saved = invoiceRepository.save(invoice);
        assertNotNull(saved.getId());

        Optional<Invoice> found = invoiceRepository.findByInvoiceNumber("INV-001");
        assertTrue(found.isPresent());
        assertEquals(user.getId(), found.get().getUserId().getId());
    }

    @Test
    void testFindLatestInvoiceNumber() {
        User user = TestUserFactory.createViewer("test2@example.com");
        user = userRepository.save(user);

        Invoice inv1 = Invoice.builder()
                .userId(user)
                .invoiceNumber("INV-2026-000001")
                .grossAmount(new BigDecimal("10.00"))
                .vatAmount(new BigDecimal("2.00"))
                .netAmount(new BigDecimal("8.00"))
                .currency("EUR")
                .countryCode("FR")
                .invoiceType(InvoiceType.SUBSCRIPTION)
                .status(InvoiceStatus.PAID)
                .issuedAt(Instant.now())
                .build();
        invoiceRepository.save(inv1);

        Invoice inv2 = Invoice.builder()
                .userId(user)
                .invoiceNumber("INV-2026-000005")
                .grossAmount(new BigDecimal("10.00"))
                .vatAmount(new BigDecimal("2.00"))
                .netAmount(new BigDecimal("8.00"))
                .currency("EUR")
                .countryCode("FR")
                .invoiceType(InvoiceType.SUBSCRIPTION)
                .status(InvoiceStatus.PAID)
                .issuedAt(Instant.now())
                .build();
        invoiceRepository.save(inv2);

        Optional<String> latest = invoiceRepository.findLatestInvoiceNumber("INV-2026-");
        assertTrue(latest.isPresent());
        assertEquals("INV-2026-000005", latest.get());

        Optional<String> none = invoiceRepository.findLatestInvoiceNumber("INV-2027-");
        assertTrue(none.isEmpty());
    }
}








