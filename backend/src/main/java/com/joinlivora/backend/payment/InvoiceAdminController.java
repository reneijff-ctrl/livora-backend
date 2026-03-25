package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.InvoiceAdminDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class InvoiceAdminController {

    private final InvoiceAdminService invoiceAdminService;

    @GetMapping
    public ResponseEntity<Page<InvoiceAdminDTO>> listInvoices(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) InvoiceType type,
            Pageable pageable
    ) {
        return ResponseEntity.ok(invoiceAdminService.getInvoices(startDate, endDate, countryCode, type, pageable));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportInvoices(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) InvoiceType type
    ) {
        byte[] csvBytes = invoiceAdminService.exportInvoicesToCsv(startDate, endDate, countryCode, type);
        
        String filename = "invoices-export-" + Instant.now().toString().substring(0, 10) + ".csv";
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvBytes);
    }
}
