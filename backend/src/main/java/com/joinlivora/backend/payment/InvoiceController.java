package com.joinlivora.backend.payment;

import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final InvoicePdfService invoicePdfService;
    private final UserService userService;

    @GetMapping("/{invoiceId}/download")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PREMIUM', 'CREATOR')")
    public ResponseEntity<byte[]> downloadInvoice(
            @PathVariable UUID invoiceId,
            @AuthenticationPrincipal UserDetails userDetails
    ) throws IOException {
        log.info("SECURITY: Invoice download requested for ID: {} by creator: {}", invoiceId, userDetails.getUsername());
        
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        User currentUser = userService.getByEmail(userDetails.getUsername());
        
        // Ownership check: User can only download their own invoices, unless they are an ADMIN
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (!isAdmin && !invoice.getUserId().getId().equals(currentUser.getId())) {
            log.warn("SECURITY: User {} attempted to download invoice {} belonging to creator {}",
                    currentUser.getEmail(), invoiceId, invoice.getUserId().getEmail());
            throw new AccessDeniedException("You do not have permission to download this invoice");
        }

        byte[] pdfBytes = invoicePdfService.generateInvoicePdf(invoice);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + invoice.getInvoiceNumber() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }
}
