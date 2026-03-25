package com.joinlivora.backend.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    Optional<Invoice> findByStripeInvoiceId(String stripeInvoiceId);
    List<Invoice> findByUserId_Id(Long userId);

    @Query(value = "SELECT invoice_number FROM invoices WHERE invoice_number LIKE :prefix% ORDER BY invoice_number DESC LIMIT 1", nativeQuery = true)
    Optional<String> findLatestInvoiceNumber(@Param("prefix") String prefix);

    @Query("SELECT SUM(i.netAmount) FROM Invoice i WHERE i.issuedAt BETWEEN :start AND :end")
    BigDecimal sumNetAmountByPeriod(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT SUM(i.vatAmount) FROM Invoice i WHERE i.issuedAt BETWEEN :start AND :end")
    BigDecimal sumVatAmountByPeriod(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT i.countryCode, SUM(i.netAmount) FROM Invoice i WHERE i.issuedAt BETWEEN :start AND :end GROUP BY i.countryCode")
    List<Object[]> sumNetAmountByCountryAndPeriod(@Param("start") Instant start, @Param("end") Instant end);
}
