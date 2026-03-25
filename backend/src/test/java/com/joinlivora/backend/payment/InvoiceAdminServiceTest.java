package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.InvoiceAdminDTO;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class InvoiceAdminServiceTest {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UserRepository userRepository;

    private InvoiceAdminService invoiceAdminService;
    private User testUser;

    @BeforeEach
    void setup() {
        invoiceRepository.deleteAll();
        userRepository.deleteAll();
        
        invoiceAdminService = new InvoiceAdminService(invoiceRepository);
        testUser = TestUserFactory.createUser("admin_test_user_" + System.nanoTime() + "@example.com", Role.USER);
        testUser = userRepository.save(testUser);
        
        long suffix = System.nanoTime();
        
        invoiceRepository.save(Invoice.builder()
                .userId(testUser)
                .invoiceNumber("INV-FR-" + suffix)
                .grossAmount(BigDecimal.TEN)
                .vatAmount(BigDecimal.ONE)
                .netAmount(new BigDecimal("9.00"))
                .currency("EUR")
                .countryCode("FR")
                .invoiceType(InvoiceType.SUBSCRIPTION)
                .status(InvoiceStatus.PAID)
                .issuedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build());

        invoiceRepository.save(Invoice.builder()
                .userId(testUser)
                .invoiceNumber("INV-US-" + suffix)
                .grossAmount(BigDecimal.TEN)
                .vatAmount(BigDecimal.ONE)
                .netAmount(new BigDecimal("9.00"))
                .currency("EUR")
                .countryCode("US")
                .invoiceType(InvoiceType.TOKENS)
                .status(InvoiceStatus.PAID)
                .issuedAt(Instant.now())
                .build());
    }

    @Test
    void getInvoices_WithCountryFilter_ShouldReturnFiltered() {
        Page<InvoiceAdminDTO> result = invoiceAdminService.getInvoices(null, null, "FR", null, PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().get(0).getInvoiceNumber().startsWith("INV-FR-"));
    }

    @Test
    void getInvoices_WithTypeFilter_ShouldReturnFiltered() {
        Page<InvoiceAdminDTO> result = invoiceAdminService.getInvoices(null, null, null, InvoiceType.TOKENS, PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().get(0).getInvoiceNumber().startsWith("INV-US-"));
    }

    @Test
    void getInvoices_WithDateFilter_ShouldReturnFiltered() {
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Page<InvoiceAdminDTO> result = invoiceAdminService.getInvoices(start, null, null, null, PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().get(0).getInvoiceNumber().startsWith("INV-US-"));
    }

    @Test
    void exportInvoicesToCsv_ShouldReturnCorrectContent() {
        byte[] csvBytes = invoiceAdminService.exportInvoicesToCsv(null, null, "US", null);
        String csv = new String(csvBytes);
        assertTrue(csv.contains("INV-US-"));
        assertFalse(csv.contains("INV-FR-"));
        assertTrue(csv.contains("TOKENS"));
        assertTrue(csv.contains(testUser.getEmail()));
    }
}








