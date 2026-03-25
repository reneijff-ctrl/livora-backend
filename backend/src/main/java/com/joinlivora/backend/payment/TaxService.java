package com.joinlivora.backend.payment;

import com.joinlivora.backend.payment.dto.TaxSummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("taxService")
@RequiredArgsConstructor
public class TaxService {

    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public TaxSummaryDTO getTaxSummary(Instant start, Instant end) {
        BigDecimal totalRevenue = invoiceRepository.sumNetAmountByPeriod(start, end);
        BigDecimal totalVat = invoiceRepository.sumVatAmountByPeriod(start, end);
        List<Object[]> countryStats = invoiceRepository.sumNetAmountByCountryAndPeriod(start, end);

        Map<String, BigDecimal> revenueByCountry = new HashMap<>();
        if (countryStats != null) {
            for (Object[] result : countryStats) {
                String country = (String) result[0];
                BigDecimal amount = (BigDecimal) result[1];
                revenueByCountry.put(country, amount != null ? amount : BigDecimal.ZERO);
            }
        }

        return TaxSummaryDTO.builder()
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .totalVat(totalVat != null ? totalVat : BigDecimal.ZERO)
                .revenueByCountry(revenueByCountry)
                .build();
    }
}
