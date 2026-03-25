package com.joinlivora.backend.payout;

import com.joinlivora.backend.config.PlatformCompanyProperties;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

@Service("creatorEarningsInvoiceService")
@RequiredArgsConstructor
@Slf4j
public class CreatorEarningsInvoiceService {

    private final CreatorEarningRepository earningRepository;
    private final CreatorEarningsInvoiceRepository invoiceRepository;
    private final LegacyCreatorProfileRepository creatorProfileRepository;
    private final PlatformCompanyProperties companyProperties;

    @Transactional
    public void generateMonthlyInvoices(YearMonth month) {
        log.info("Starting monthly invoice generation for {}", month);
        
        LocalDateTime startOfMonth = month.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = month.atEndOfMonth().atTime(LocalTime.MAX);
        
        Instant start = startOfMonth.toInstant(ZoneOffset.UTC);
        Instant end = endOfMonth.toInstant(ZoneOffset.UTC);

        List<User> creators = earningRepository.findCreatorsWithUninvoicedEarnings(start, end);
        log.info("Found {} creators with uninvoiced earnings for {}", creators.size(), month);

        for (User creator : creators) {
            try {
                List<String> currencies = earningRepository.findCurrenciesByCreatorWithUninvoicedEarnings(creator, start, end);
                for (String currency : currencies) {
                    generateInvoiceForCreatorAndCurrency(creator, currency, start, end, month);
                }
            } catch (Exception e) {
                log.error("Failed to generate invoices for creator {} for period {}", creator.getEmail(), month, e);
            }
        }
    }

    private void generateInvoiceForCreatorAndCurrency(User creator, String currency, Instant start, Instant end, YearMonth month) {
        List<CreatorEarning> earnings = earningRepository.findUninvoicedEarningsByCreatorCurrencyAndPeriod(creator, currency, start, end);
        if (earnings.isEmpty()) return;

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;

        for (CreatorEarning earning : earnings) {
            gross = gross.add(earning.getGrossAmount());
            fee = fee.add(earning.getPlatformFee());
            net = net.add(earning.getNetAmount());
        }

        String invoiceNumber = generateInvoiceNumber(creator, currency, month);
        
        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(creator).orElse(null);

        CreatorEarningsInvoice invoice = CreatorEarningsInvoice.builder()
                .creator(creator)
                .invoiceNumber(invoiceNumber)
                .periodStart(start)
                .periodEnd(end)
                .grossEarnings(gross)
                .platformFee(fee)
                .netEarnings(net)
                .currency(currency)
                .creatorName(profile != null ? profile.getDisplayName() : creator.getEmail())
                .creatorEmail(creator.getEmail())
                .sellerName(companyProperties.getName())
                .sellerAddress(companyProperties.getAddress())
                .sellerEmail(companyProperties.getEmail())
                .sellerVatNumber(companyProperties.getVatNumber())
                .status(CreatorEarningsInvoiceStatus.GENERATED)
                .build();

        invoice = invoiceRepository.save(invoice);

        for (CreatorEarning earning : earnings) {
            earning.setInvoice(invoice);
            earning.setLocked(true);
            earningRepository.save(earning);
        }

        log.info("Generated invoice {} for creator {} ({}) for period {}", invoiceNumber, creator.getEmail(), currency, month);
    }

    private String generateInvoiceNumber(User creator, String currency, YearMonth month) {
        // Format: CR-INV-{YEAR}{MONTH}-{USERID}-{CURRENCY}
        return String.format("CR-INV-%d%02d-%d-%s", month.getYear(), month.getMonthValue(), creator.getId(), currency.toUpperCase());
    }
}
