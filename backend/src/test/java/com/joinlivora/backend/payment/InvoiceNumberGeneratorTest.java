package com.joinlivora.backend.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceNumberGeneratorTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private InvoiceNumberGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new InvoiceNumberGenerator(invoiceRepository);
    }

    @Test
    void generateNextInvoiceNumber_NoExistingInvoices_ShouldStartFromOne() {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        String prefix = "INV-" + year + "-";
        when(invoiceRepository.findLatestInvoiceNumber(prefix)).thenReturn(Optional.empty());

        String result = generator.generateNextInvoiceNumber();

        assertEquals(prefix + "000001", result);
    }

    @Test
    void generateNextInvoiceNumber_ExistingInvoices_ShouldIncrement() {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        String prefix = "INV-" + year + "-";
        String lastInvoice = prefix + "000042";
        when(invoiceRepository.findLatestInvoiceNumber(prefix)).thenReturn(Optional.of(lastInvoice));

        String result = generator.generateNextInvoiceNumber();

        assertEquals(prefix + "000043", result);
    }

    @Test
    void generateNextInvoiceNumber_LargeNumber_ShouldIncrementCorrectly() {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        String prefix = "INV-" + year + "-";
        String lastInvoice = prefix + "000999";
        when(invoiceRepository.findLatestInvoiceNumber(prefix)).thenReturn(Optional.of(lastInvoice));

        String result = generator.generateNextInvoiceNumber();

        assertEquals(prefix + "001000", result);
    }

    @Test
    void generateNextInvoiceNumber_MalformedExistingNumber_ShouldFallbackToOne() {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        String prefix = "INV-" + year + "-";
        String lastInvoice = "MALFORMED";
        when(invoiceRepository.findLatestInvoiceNumber(prefix)).thenReturn(Optional.of(lastInvoice));

        String result = generator.generateNextInvoiceNumber();

        assertEquals(prefix + "000001", result);
    }
}








