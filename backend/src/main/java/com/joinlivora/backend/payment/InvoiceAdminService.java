package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.InvoiceAdminDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service("invoiceAdminService")
@RequiredArgsConstructor
public class InvoiceAdminService {

    private final InvoiceRepository invoiceRepository;

    public Page<InvoiceAdminDTO> getInvoices(Instant startDate, Instant endDate, String countryCode, InvoiceType type, Pageable pageable) {
        Specification<Invoice> spec = Specification.where(InvoiceSpecification.hasDateBetween(startDate, endDate))
                .and(InvoiceSpecification.hasCountryCode(countryCode))
                .and(InvoiceSpecification.hasInvoiceType(type));
        return invoiceRepository.findAll(spec, pageable).map(InvoiceAdminDTO::fromEntity);
    }

    public byte[] exportInvoicesToCsv(Instant startDate, Instant endDate, String countryCode, InvoiceType type) {
        Specification<Invoice> spec = Specification.where(InvoiceSpecification.hasDateBetween(startDate, endDate))
                .and(InvoiceSpecification.hasCountryCode(countryCode))
                .and(InvoiceSpecification.hasInvoiceType(type));
        List<Invoice> invoices = invoiceRepository.findAll(spec);

        StringBuilder csv = new StringBuilder();
        csv.append("Invoice Number,Date,User Email,Type,Country,Net Amount,VAT Amount,Gross Amount,Currency,Status\n");

        for (Invoice invoice : invoices) {
            csv.append(escapeCsv(invoice.getInvoiceNumber())).append(",")
                    .append(invoice.getIssuedAt()).append(",")
                    .append(escapeCsv(invoice.getUserId().getEmail())).append(",")
                    .append(invoice.getInvoiceType()).append(",")
                    .append(escapeCsv(invoice.getCountryCode())).append(",")
                    .append(invoice.getNetAmount()).append(",")
                    .append(invoice.getVatAmount()).append(",")
                    .append(invoice.getGrossAmount()).append(",")
                    .append(escapeCsv(invoice.getCurrency())).append(",")
                    .append(invoice.getStatus()).append("\n");
        }

        return csv.toString().getBytes();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
