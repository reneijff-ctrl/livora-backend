package com.joinlivora.backend.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

@Service("invoiceNumberGenerator")
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private final InvoiceRepository invoiceRepository;

    /**
     * Generates the next invoice number in the format INV-{YEAR}-{SEQUENTIAL_NUMBER}.
     * Sequential number is 6-digit padded for proper sorting and uniqueness.
     * Example: INV-2026-000001
     *
     * @return the generated invoice number
     */
    public String generateNextInvoiceNumber() {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        String prefix = "INV-" + year + "-";
        
        Optional<String> latestNumber = invoiceRepository.findLatestInvoiceNumber(prefix);
        
        long nextSequentialNumber = 1;
        if (latestNumber.isPresent()) {
            String lastInumber = latestNumber.get();
            try {
                String numberPart = lastInumber.substring(prefix.length());
                nextSequentialNumber = Long.parseLong(numberPart) + 1;
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                // Fallback to 1 if parsing fails, or we could look for other entries
            }
        }
        
        return String.format("%s%06d", prefix, nextSequentialNumber);
    }
}
