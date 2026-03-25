package com.joinlivora.backend.payment;

import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;

public class InvoiceSpecification {

    public static Specification<Invoice> hasDateBetween(Instant start, Instant end) {
        return (root, query, cb) -> {
            if (start == null && end == null) return null;
            if (start != null && end != null) return cb.between(root.get("issuedAt"), start, end);
            if (start != null) return cb.greaterThanOrEqualTo(root.get("issuedAt"), start);
            return cb.lessThanOrEqualTo(root.get("issuedAt"), end);
        };
    }

    public static Specification<Invoice> hasCountryCode(String countryCode) {
        return (root, query, cb) -> countryCode == null ? null : cb.equal(root.get("countryCode"), countryCode.toUpperCase());
    }

    public static Specification<Invoice> hasInvoiceType(InvoiceType type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("invoiceType"), type);
    }
}
